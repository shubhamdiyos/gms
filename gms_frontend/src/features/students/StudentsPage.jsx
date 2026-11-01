import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Box,
  Typography,
  Button,
  Paper,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  Grid,
  Chip,
  IconButton,
  Tooltip,
  MenuItem,
  FormControl,
  InputLabel,
  Select,
  InputAdornment,
} from '@mui/material';
import { DataGrid } from '@mui/x-data-grid';
import {
  Add,
  Edit,
  Person,
  Search,
  ToggleOff,
  ToggleOn,
} from '@mui/icons-material';
import { useForm, Controller } from 'react-hook-form';
import { yupResolver } from '@hookform/resolvers/yup';
import * as yup from 'yup';
import {
  useGetStudentSearchQuery,
  useCreateStudentMutation,
  useUpdateStudentMutation,
  useToggleStudentMutation,
  useGetClassroomsQuery,
  useGetSectionsQuery,
} from '../../store/api/apiSlice';
import { toast } from 'react-toastify';

const schema = yup.object({
  firstName: yup.string().required('First name is required'),
  lastName: yup.string().required('Last name is required'),
  email: yup.string().email('Invalid email').required('Email is required'),
  phoneNumber: yup.string().required('Phone number is required'),
  dateOfBirth: yup.date().required('Date of birth is required'),
  gender: yup.string().required('Gender is required'),
  classId: yup.number().required('Classroom is required'),
  sectionId: yup
    .number()
    .nullable()
    .transform((value, original) => (original === '' || original == null ? null : value)),
});

/** Matches the debounce the page applies to the free-text box before it becomes a request. */
const SEARCH_DEBOUNCE_MS = 350;

/**
 * Grid column field -> the `Student` property name the backend sorts on.
 *
 * `StudentServiceImpl.SORTABLE_STUDENT_FIELDS` whitelists only
 * `id, firstName, lastName, email, studentId, dateOfBirth, gender`; anything else the client sends
 * is silently replaced by the server default (`lastName asc`), so a column that is not listed here
 * is rendered `sortable: false` rather than offering a sort that does nothing.
 *
 * The "Full Name" column is a display of `firstName` + `lastName`, so it sorts by `lastName`.
 * `classId` (a classroom association, not a Student property) and `phoneNumber`/`status` are not
 * on the whitelist at all.
 */
const SORT_FIELD_MAP = {
  studentId: 'studentId',
  fullName: 'lastName',
  email: 'email',
  gender: 'gender',
};

const PAGE_SIZE_OPTIONS = [10, 25, 50];

/**
 * The grid's no-rows slot.
 *
 * Three distinct states, never collapsed into "no students": the search failed or returned a payload
 * that is not a page (`hasError`), the search succeeded but matched nothing (`hasActiveFilters`, with
 * the server's free-text query and classroom echoed back so the user can see what was actually asked),
 * and the school genuinely has no students yet.
 */
const StudentsGridEmptyRow = ({ hasError, hasActiveFilters, query, classroomName, onClearFilters }) => (
  <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', height: '100%', gap: 1 }}>
    <Person sx={{ fontSize: 40, color: hasError ? 'error.main' : 'text.disabled' }} />
    <Typography variant="subtitle1">
      {hasError
        ? 'Student list unavailable'
        : hasActiveFilters
          ? 'No students match this search'
          : 'No students yet'}
    </Typography>
    <Typography variant="body2" color="text.secondary" sx={{ maxWidth: 460, textAlign: 'center' }}>
      {hasError
        ? 'The search request did not return a usable page. The grid is empty because there is no result, not because the school has no students — see the error above.'
        : hasActiveFilters
          ? `Looking for${query ? ` “${query}”` : ' text'}${
              classroomName ? ` in ${classroomName}` : ''
            }. Try a different name, student ID, email, or classroom.`
          : 'Add a student to see them here.'}
    </Typography>
    {!hasError && hasActiveFilters && (
      <Button size="small" onClick={onClearFilters}>
        Clear search
      </Button>
    )}
  </Box>
);

const StudentsPage = () => {
  const [open, setOpen] = useState(false);
  const [editingStudent, setEditingStudent] = useState(null);

  // Search state. `searchText` is the box, `debouncedQuery` is what actually goes to the server.
  const [searchText, setSearchText] = useState('');
  const [debouncedQuery, setDebouncedQuery] = useState('');
  const [classIdFilter, setClassIdFilter] = useState('');
  // Server-side page/sort models; `page` is 0-based in MUI X and in the backend, so no conversion.
  const [paginationModel, setPaginationModel] = useState({ page: 0, pageSize: PAGE_SIZE_OPTIONS[0] });
  // Seeded with the server's own default (`lastName asc`, StudentServiceImpl.DEFAULT_SEARCH_FIELD) on the
  // column that displays it, so the header arrow matches the order the rows actually arrive in.
  const [sortModel, setSortModel] = useState([{ field: 'fullName', sort: 'asc' }]);

  useEffect(() => {
    const timer = setTimeout(() => setDebouncedQuery(searchText.trim()), SEARCH_DEBOUNCE_MS);
    return () => clearTimeout(timer);
  }, [searchText]);

  /**
   * Keep the visible sort and the sent sort describing the same thing.
   *
   * The grid's third click clears the model, and an unmapped column would send a field the whitelist
   * discards; in both cases the server still returns `lastName asc`, so re-seed the default rather than
   * rendering an arrowless list that is quietly sorted. Only one column is ever sorted because the
   * backend's sort grammar takes one `field,direction`.
   */
  const handleSortModelChange = useCallback((model) => {
    const item = model[0];
    if (!item || !item.sort || !SORT_FIELD_MAP[item.field]) {
      setSortModel([{ field: 'fullName', sort: 'asc' }]);
      return;
    }
    setSortModel([item]);
  }, []);

  /**
   * Only ever emit a `sort` the whitelist accepts. The grid field is translated through
   * SORT_FIELD_MAP; a model that does not map sends no `sort`, which leaves the server on its own
   * default (`lastName asc`) instead of a parameter the backend would discard anyway.
   */
  const sortParam = useMemo(() => {
    const item = sortModel[0];
    if (!item || !item.sort) return undefined;
    const serverField = SORT_FIELD_MAP[item.field];
    if (!serverField) return undefined;
    return `${serverField},${item.sort === 'desc' ? 'desc' : 'asc'}`;
  }, [sortModel]);

  // Every new filter, query, or sort invalidates the page you are on, so return to page 0.
  useEffect(() => {
    setPaginationModel((model) => (model.page === 0 ? model : { ...model, page: 0 }));
  }, [debouncedQuery, classIdFilter, sortParam]);

  const searchArgs = useMemo(
    () => ({
      query: debouncedQuery || undefined,
      classId: classIdFilter === '' ? undefined : Number(classIdFilter),
      page: paginationModel.page,
      size: paginationModel.pageSize,
      sort: sortParam,
    }),
    [debouncedQuery, classIdFilter, paginationModel, sortParam]
  );

  // `GET /students/search` answers a Spring Data Page of StudentResponse (camelCase), not the raw
  // entity list `GET /students` returns, so rows and totals come from this one response.
  const {
    data: searchPage,
    isLoading,
    isFetching,
    isError,
    error: searchError,
    refetch,
  } = useGetStudentSearchQuery(searchArgs);

  const { data: classrooms = [], error: classroomsError } = useGetClassroomsQuery();
  const { data: sections = [] } = useGetSectionsQuery();

  const rows = Array.isArray(searchPage?.content) ? searchPage.content : [];
  const totalElements = Number.isFinite(searchPage?.totalElements) ? searchPage.totalElements : 0;
  const serverPageNumber = Number.isFinite(searchPage?.number) ? searchPage.number : paginationModel.page;
  const serverPageSize = Number.isFinite(searchPage?.size) ? searchPage.size : paginationModel.pageSize;
  const rangeStart = totalElements === 0 ? 0 : serverPageNumber * serverPageSize + 1;
  const rangeEnd = Math.min((serverPageNumber + 1) * serverPageSize, totalElements);

  /**
   * A Spring Data `Page` (`content[]` + `totalElements`) is the only payload this grid will render.
   * Anything else — a bare array such as the mock service's fallback for an unhandled route, or an
   * unexpected body from a proxy — is not "zero students", it is a read that did not answer, so it is
   * reported as a failure rather than silently producing an empty list.
   */
  const isPagePayload = Array.isArray(searchPage?.content) && Number.isFinite(searchPage?.totalElements);
  const unexpectedPayload = searchPage != null && !isPagePayload;

  const hasActiveFilters = Boolean(debouncedQuery) || classIdFilter !== '';
  const listIsBroken = isError || unexpectedPayload;
  // The classroom label the empty state echoes back, resolved without depending on the column helper
  // that is declared further down the component.
  const selectedClassroomName = useMemo(() => {
    if (classIdFilter === '') return null;
    const match = classrooms.find((classroom) => classroom.id === Number(classIdFilter));
    return match ? match.name : 'Unknown';
  }, [classIdFilter, classrooms]);

  const clearFilters = useCallback(() => {
    setSearchText('');
    setDebouncedQuery('');
    setClassIdFilter('');
  }, []);

  const [createStudent] = useCreateStudentMutation();
  const [updateStudent] = useUpdateStudentMutation();
  const [toggleStudent] = useToggleStudentMutation();

  const {
    register,
    handleSubmit,
    reset,
    control,
    formState: { errors },
  } = useForm({
    resolver: yupResolver(schema),
  });

  const handleOpen = (student = null) => {
    setEditingStudent(student);
    if (student) {
      // StudentResponse is camelCase throughout (classId / sectionId), unlike the entity JSON
      // returned by GET /students, which spells those keys class_id / section_id.
      reset({
        firstName: student.firstName || '',
        lastName: student.lastName || '',
        email: student.email || '',
        phoneNumber: student.phoneNumber || '',
        dateOfBirth: student.dateOfBirth || '',
        gender: student.gender || '',
        classId: student.classId ?? '',
        sectionId: student.sectionId ?? '',
      });
    } else {
      reset();
    }
    setOpen(true);
  };

  const handleClose = () => {
    setOpen(false);
    setEditingStudent(null);
    reset();
  };

  const onSubmit = async (data) => {
    try {
      const payload = {
        ...data,
        classId: Number(data.classId),
        sectionId: data.sectionId === '' || data.sectionId == null ? null : Number(data.sectionId),
      };
      if (editingStudent) {
        await updateStudent({ id: editingStudent.id, ...payload }).unwrap();
        toast.success('Student updated successfully');
      } else {
        await createStudent(payload).unwrap();
        toast.success('Student created successfully');
      }
      handleClose();
      refetch();
    } catch {
      toast.error('Operation failed');
    }
  };

  const handleToggle = async (id, isActive) => {
    try {
      await toggleStudent({ id, isActive }).unwrap();
      toast.success(`Student ${isActive ? 'activated' : 'deactivated'} successfully`);
      refetch();
    } catch {
      toast.error('Toggle failed');
    }
  };

  const getClassroomName = (classId) => {
    const classroom = classrooms.find((c) => c.id === classId);
    return classroom ? classroom.name : 'Unknown';
  };

  const columns = [
    {
      field: 'studentId',
      headerName: 'Student ID',
      width: 120,
    },
    {
      field: 'fullName',
      headerName: 'Full Name',
      width: 200,
      // Not a row property: StudentResponse carries firstName / lastName separately.
      renderCell: (params) => (
        <Box sx={{ display: 'flex', alignItems: 'center' }}>
          <Person sx={{ mr: 1, color: '#1976d2' }} />
          {`${params.row.firstName ?? ''} ${params.row.lastName ?? ''}`.trim() || '—'}
        </Box>
      ),
    },
    {
      field: 'email',
      headerName: 'Email',
      width: 200,
    },
    {
      field: 'phoneNumber',
      headerName: 'Phone',
      width: 130,
      sortable: false,
    },
    {
      field: 'classId',
      headerName: 'Class',
      width: 120,
      sortable: false,
      renderCell: (params) => getClassroomName(params.value),
    },
    {
      field: 'gender',
      headerName: 'Gender',
      width: 100,
    },
    {
      field: 'status',
      headerName: 'Status',
      width: 100,
      sortable: false,
      renderCell: (params) => (
        <Chip
          label={params.value === '1' ? 'Active' : 'Inactive'}
          color={params.value === '1' ? 'success' : 'error'}
          size="small"
        />
      ),
    },
    {
      field: 'actions',
      headerName: 'Actions',
      width: 140,
      sortable: false,
      renderCell: (params) => (
        <Box>
          <Tooltip title="Edit">
            <IconButton size="small" onClick={() => handleOpen(params.row)}>
              <Edit />
            </IconButton>
          </Tooltip>
          <Tooltip title={params.row.status === '1' ? 'Deactivate' : 'Activate'}>
            <IconButton
              size="small"
              onClick={() => handleToggle(params.row.id, params.row.status !== '1')}
            >
              {params.row.status === '1' ? <ToggleOn color="success" /> : <ToggleOff />}
            </IconButton>
          </Tooltip>
        </Box>
      ),
    },
  ];

  const noRowsOverlay = useMemo(
    () => () => (
      <StudentsGridEmptyRow
        hasError={listIsBroken}
        hasActiveFilters={hasActiveFilters}
        query={debouncedQuery}
        classroomName={selectedClassroomName}
        onClearFilters={clearFilters}
      />
    ),
    [listIsBroken, hasActiveFilters, debouncedQuery, selectedClassroomName, clearFilters]
  );

  const errorMessage = useMemo(() => {
    if (unexpectedPayload && !isError) {
      return 'Student search returned something that is not a page of results, so nothing can be shown. The backend route (or the mock service, when VITE_MOCK_AUTH=true) may not implement it.';
    }
    if (!isError) return null;
    const status = searchError?.status;
    const detail = searchError?.data?.message || searchError?.error?.message;
    if (status === 401) {
      return 'The server refused this search (401): the signed-in token carries no school, so there is no tenant to search within.';
    }
    if (status === 403) {
      return 'This account is not allowed to search students (403). The list below is empty because the request was refused, not because the school has no students.';
    }
    return detail || `Student search failed${status ? ` (${status})` : ''}.`;
  }, [isError, unexpectedPayload, searchError]);

  return (
    <Box>
      {/* Header */}
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
        <Box>
          <Typography variant="h4" component="h1" gutterBottom>
            Students Management
          </Typography>
          <Typography variant="body1" color="text.secondary">
            Manage student records and information
          </Typography>
        </Box>
        <Button
          variant="contained"
          startIcon={<Add />}
          onClick={() => handleOpen()}
          sx={{
            background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
            '&:hover': {
              background: 'linear-gradient(135deg, #5a6fd8 0%, #6a4190 100%)',
            },
          }}
        >
          Add Student
        </Button>
      </Box>

      {/* Search + classroom filter: both are server-side, never client-side row filters */}
      <Box sx={{ display: 'flex', gap: 2, mb: 2, flexWrap: 'wrap', alignItems: 'center' }}>
        <TextField
          value={searchText}
          onChange={(event) => setSearchText(event.target.value)}
          placeholder="Search by name, student ID, or email"
          size="small"
          sx={{ width: 340 }}
          InputProps={{
            startAdornment: (
              <InputAdornment position="start">
                <Search fontSize="small" />
              </InputAdornment>
            ),
          }}
        />
        <FormControl size="small" sx={{ minWidth: 200 }}>
          <InputLabel id="classroom-filter-label">Classroom</InputLabel>
          <Select
            labelId="classroom-filter-label"
            label="Classroom"
            value={classIdFilter}
            onChange={(event) => setClassIdFilter(event.target.value)}
          >
            <MenuItem value="">All classrooms</MenuItem>
            {classrooms.map((classroom) => (
              <MenuItem key={classroom.id} value={classroom.id}>
                {classroom.name}
              </MenuItem>
            ))}
          </Select>
        </FormControl>
        <Typography variant="body2" color="text.secondary">
          {totalElements > 0 && !listIsBroken ? `${rangeStart}–${rangeEnd} of ${totalElements} students` : ''}
        </Typography>
      </Box>

      {listIsBroken && (
        <Alert
          severity="error"
          sx={{ mb: 2 }}
          action={
            <Button size="small" color="inherit" onClick={() => refetch()}>
              Retry
            </Button>
          }
        >
          {errorMessage}
        </Alert>
      )}

      {classroomsError && (
        <Alert severity="warning" sx={{ mb: 2 }}>
          Classrooms could not be loaded, so the classroom filter has no options; name/ID/email search
          still works.
        </Alert>
      )}

      {/* Data Grid */}
      <Paper sx={{ height: 600, width: '100%' }}>
        <DataGrid
          rows={rows}
          columns={columns}
          loading={isLoading || isFetching}
          paginationMode="server"
          paginationModel={paginationModel}
          onPaginationModelChange={setPaginationModel}
          rowCount={totalElements}
          sortingMode="server"
          sortModel={sortModel}
          onSortModelChange={handleSortModelChange}
          disableMultipleColumnsSorting
          slots={{ noRowsOverlay }}
          pageSizeOptions={PAGE_SIZE_OPTIONS}
          disableRowSelectionOnClick
          sx={{
            '& .MuiDataGrid-cell:hover': {
              color: 'primary.main',
            },
          }}
        />
      </Paper>

      {/* Add/Edit Dialog */}
      <Dialog open={open} onClose={handleClose} maxWidth="md" fullWidth>
        <form onSubmit={handleSubmit(onSubmit)}>
          <DialogTitle>
            {editingStudent ? 'Edit Student' : 'Add New Student'}
          </DialogTitle>
          <DialogContent>
            <Grid container spacing={2} sx={{ mt: 1 }}>
              <Grid item xs={12} md={6}>
                <TextField
                  {...register('firstName')}
                  fullWidth
                  label="First Name"
                  error={!!errors.firstName}
                  helperText={errors.firstName?.message}
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField
                  {...register('lastName')}
                  fullWidth
                  label="Last Name"
                  error={!!errors.lastName}
                  helperText={errors.lastName?.message}
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField
                  {...register('email')}
                  fullWidth
                  label="Email"
                  type="email"
                  error={!!errors.email}
                  helperText={errors.email?.message}
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField
                  {...register('phoneNumber')}
                  fullWidth
                  label="Phone Number"
                  error={!!errors.phoneNumber}
                  helperText={errors.phoneNumber?.message}
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField
                  {...register('dateOfBirth')}
                  fullWidth
                  label="Date of Birth"
                  type="date"
                  InputLabelProps={{ shrink: true }}
                  error={!!errors.dateOfBirth}
                  helperText={errors.dateOfBirth?.message}
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <FormControl fullWidth error={!!errors.gender}>
                  <InputLabel>Gender</InputLabel>
                  <Controller
                    name="gender"
                    control={control}
                    render={({ field }) => (
                      <Select {...field} label="Gender">
                        <MenuItem value="MALE">Male</MenuItem>
                        <MenuItem value="FEMALE">Female</MenuItem>
                        <MenuItem value="OTHER">Other</MenuItem>
                      </Select>
                    )}
                  />
                  {errors.gender && (
                    <Typography variant="caption" color="error" sx={{ mt: 1, ml: 2 }}>
                      {errors.gender?.message}
                    </Typography>
                  )}
                </FormControl>
              </Grid>
              <Grid item xs={12} md={6}>
                <FormControl fullWidth error={!!errors.classId}>
                  <InputLabel>Classroom</InputLabel>
                  <Controller
                    name="classId"
                    control={control}
                    render={({ field }) => (
                      <Select {...field} label="Classroom" value={field.value ?? ''}>
                        {classrooms.map((classroom) => (
                          <MenuItem key={classroom.id} value={classroom.id}>
                            {classroom.name}
                          </MenuItem>
                        ))}
                      </Select>
                    )}
                  />
                  {errors.classId && (
                    <Typography variant="caption" color="error" sx={{ mt: 1, ml: 2 }}>
                      {errors.classId?.message}
                    </Typography>
                  )}
                </FormControl>
              </Grid>
              <Grid item xs={12} md={6}>
                <FormControl fullWidth error={!!errors.sectionId}>
                  <InputLabel>Section</InputLabel>
                  <Controller
                    name="sectionId"
                    control={control}
                    render={({ field }) => (
                      <Select {...field} label="Section" value={field.value ?? ''}>
                        <MenuItem value="">No Section</MenuItem>
                        {sections.map((section) => (
                          <MenuItem key={section.id} value={section.id}>
                            {section.name}
                          </MenuItem>
                        ))}
                      </Select>
                    )}
                  />
                  {errors.sectionId && (
                    <Typography variant="caption" color="error" sx={{ mt: 1, ml: 2 }}>
                      {errors.sectionId?.message}
                    </Typography>
                  )}
                </FormControl>
              </Grid>
            </Grid>
          </DialogContent>
          <DialogActions>
            <Button onClick={handleClose}>Cancel</Button>
            <Button type="submit" variant="contained">
              {editingStudent ? 'Update' : 'Create'}
            </Button>
          </DialogActions>
        </form>
      </Dialog>
    </Box>
  );
};

export default StudentsPage;
