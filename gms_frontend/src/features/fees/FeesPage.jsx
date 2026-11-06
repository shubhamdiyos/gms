import { useState } from 'react';
import {
  Alert,
  Box,
  Typography,
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
  Button,
  Checkbox,
  FormControl,
  FormControlLabel,
  InputLabel,
  Select,
  MenuItem,
} from '@mui/material';
import { DataGrid } from '@mui/x-data-grid';
import {
  Add,
  Edit,
  Delete,
  Visibility,
  AttachMoney,
  AssignmentInd,
} from '@mui/icons-material';
import { useForm, Controller } from 'react-hook-form';
import { yupResolver } from '@hookform/resolvers/yup';
import * as yup from 'yup';
import {
  useGetFeesQuery,
  useCreateFeeMutation,
  useUpdateFeeMutation,
  useDeleteFeeMutation,
  useGetClassroomsQuery,
  useGetStudentSearchQuery,
  useGetStudentFeesQuery,
  useCreateStudentFeeMutation,
} from '../../store/api/apiSlice';
import { toast } from 'react-toastify';
import dayjs from 'dayjs';
import { useAuth } from '../../hooks/useAuth';
import { ROLES } from '../../constants';

const schema = yup.object({
  feeType: yup.string().required('Fee type is required'),
  amount: yup.number().positive('Amount must be positive').required('Amount is required'),
  dueDate: yup
    .string()
    .matches(/^\d{4}-\d{2}-\d{2}$/, 'Due date is required')
    .required('Due date is required'),
  academicYear: yup.string().required('Academic year is required'),
  classGrade: yup.string().nullable(),
  description: yup.string().nullable(),
  isMandatory: yup.boolean().defined(),
});

const STATUS_META = {
  ACTIVE: { label: 'Active', color: 'success' },
  INACTIVE: { label: 'Inactive', color: 'default' },
  ARCHIVED: { label: 'Archived', color: 'warning' },
};

const FeesPage = () => {
  const [open, setOpen] = useState(false);
  const [editingFee, setEditingFee] = useState(null);
  // Assigning a structure to pupils is what turns it into collectable student fees. The dialog is
  // the only client of POST /student-fees, which is ADMIN-only in the same way the structure CRUD is.
  const [assignFee, setAssignFee] = useState(null);
  const [assignSelection, setAssignSelection] = useState([]);
  const [assigning, setAssigning] = useState(false);

  // FeeController create/update/delete are hasAuthority('ADMIN'); ACCOUNTANT gets GET reads only,
  // so the write affordances must not render for it (a click would 403 and toast).
  const { hasRole } = useAuth();
  const canManageFees = hasRole(ROLES.ADMIN);

  const { data: fees = [], isLoading, isError, error, refetch } = useGetFeesQuery();
  const [createFee] = useCreateFeeMutation();
  const [updateFee] = useUpdateFeeMutation();
  const [deleteFee] = useDeleteFeeMutation();
  const [createStudentFee] = useCreateStudentFeeMutation();
  const { data: classrooms = [] } = useGetClassroomsQuery();
  const { data: studentPage } = useGetStudentSearchQuery({ page: 0, size: 100, sort: 'lastName,asc' });
  const { data: studentFees = [], refetch: refetchStudentFees } = useGetStudentFeesQuery();

  const students = Array.isArray(studentPage?.content) ? studentPage.content : [];
  const classroomName = (classId) =>
    classrooms.find((classroom) => classroom.id === classId)?.name || 'No class';
  const billedStudentIds = assignFee
    ? new Set(studentFees.filter((fee) => fee.feeId === assignFee.id).map((fee) => fee.studentId))
    : new Set();

  const handleOpenAssign = (fee) => {
    // Preselect the class this structure names, but never the pupils already billed for it.
    const billed = new Set(studentFees.filter((row) => row.feeId === fee.id).map((row) => row.studentId));
    const matching = fee.classGrade
      ? students.filter(
          (student) => classroomName(student.classId).toLowerCase() === String(fee.classGrade).toLowerCase()
        )
      : students;
    setAssignSelection(matching.filter((student) => !billed.has(student.id)).map((student) => student.id));
    setAssignFee(fee);
  };

  const closeAssign = () => {
    setAssignFee(null);
    setAssignSelection([]);
  };

  const toggleAssign = (studentId) => {
    setAssignSelection((current) =>
      current.includes(studentId)
        ? current.filter((id) => id !== studentId)
        : [...current, studentId]
    );
  };

  const handleAssign = async () => {
    if (!assignFee || assignSelection.length === 0) return;
    setAssigning(true);
    let assigned = 0;
    let failed = 0;
    for (const studentId of assignSelection) {
      try {
        await createStudentFee({ studentId, feeId: assignFee.id }).unwrap();
        assigned += 1;
      } catch {
        failed += 1;
      }
    }
    setAssigning(false);
    if (assigned > 0) {
      toast.success(`Fee assigned to ${assigned} student${assigned === 1 ? '' : 's'}`);
      refetchStudentFees();
    }
    if (failed > 0) {
      toast.error(`${failed} student${failed === 1 ? '' : 's'} could not be billed — see the ledger for details`);
    }
    if (failed === 0) closeAssign();
  };

  const {
    register,
    handleSubmit,
    reset,
    control,
    formState: { errors },
  } = useForm({
    defaultValues: { isMandatory: true },
    resolver: yupResolver(schema),
  });

  const handleOpen = (fee = null) => {
    setEditingFee(fee);
    if (fee) {
      reset({
        ...fee,
        dueDate: fee.dueDate ? String(fee.dueDate).split('T')[0] : '',
        isMandatory: Boolean(fee.isMandatory),
      });
    } else {
      reset();
    }
    setOpen(true);
  };

  const handleClose = () => {
    setOpen(false);
    setEditingFee(null);
    reset();
  };

  const onSubmit = async (data) => {
    const payload = {
      feeType: data.feeType,
      description: data.description,
      amount: data.amount,
      dueDate: data.dueDate,
      academicYear: data.academicYear,
      classGrade: data.classGrade,
      isMandatory: data.isMandatory,
    };
    try {
      if (editingFee) {
        await updateFee({ id: editingFee.id, ...payload }).unwrap();
        toast.success('Fee updated successfully');
      } else {
        await createFee(payload).unwrap();
        toast.success('Fee created successfully');
      }
      handleClose();
      refetch();
    } catch {
      toast.error('Operation failed');
    }
  };

  const handleDelete = async (id) => {
    if (window.confirm('Are you sure you want to delete this fee?')) {
      try {
        await deleteFee(id).unwrap();
        toast.success('Fee deleted successfully');
        refetch();
      } catch {
        toast.error('Delete failed');
      }
    }
  };

  const columns = [
    {
      field: 'feeType',
      headerName: 'Fee Type',
      width: 170,
      renderCell: (params) => (
        <Box sx={{ display: 'flex', alignItems: 'center' }}>
          <AttachMoney sx={{ mr: 1, color: '#4caf50' }} />
          {params.value}
        </Box>
      ),
    },
    {
      field: 'description',
      headerName: 'Description',
      width: 220,
    },
    {
      field: 'amount',
      headerName: 'Amount',
      width: 120,
      renderCell: (params) => `₹${params.value?.toLocaleString()}`,
    },
    {
      field: 'academicYear',
      headerName: 'Academic Year',
      width: 140,
    },
    {
      field: 'classGrade',
      headerName: 'Class',
      width: 110,
    },
    {
      field: 'dueDate',
      headerName: 'Due Date',
      width: 130,
      renderCell: (params) => dayjs(params.value).format('MMM DD, YYYY'),
    },
    {
      field: 'isMandatory',
      headerName: 'Mandatory',
      width: 120,
      renderCell: (params) => (
        <Chip
          label={params.value ? 'Yes' : 'No'}
          color={params.value ? 'primary' : 'default'}
          variant="outlined"
          size="small"
        />
      ),
    },
    {
      field: 'status',
      headerName: 'Status',
      width: 110,
      renderCell: (params) => {
        const meta = STATUS_META[params.value] || { label: params.value, color: 'default' };
        return <Chip label={meta.label} color={meta.color} size="small" />;
      },
    },
    ...(canManageFees
      ? [
          {
            field: 'actions',
            headerName: 'Actions',
            width: 190,
            sortable: false,
            renderCell: (params) => (
              <Box>
                <Tooltip title="View">
                  <IconButton size="small">
                    <Visibility />
                  </IconButton>
                </Tooltip>
                <Tooltip title="Assign to students">
                  <IconButton size="small" onClick={() => handleOpenAssign(params.row)}>
                    <AssignmentInd />
                  </IconButton>
                </Tooltip>
                <Tooltip title="Edit">
                  <IconButton size="small" onClick={() => handleOpen(params.row)}>
                    <Edit />
                  </IconButton>
                </Tooltip>
                <Tooltip title="Delete">
                  <IconButton size="small" onClick={() => handleDelete(params.row.id)}>
                    <Delete />
                  </IconButton>
                </Tooltip>
              </Box>
            ),
          },
        ]
      : []),
  ];

  return (
    <Box>
      {/* Header */}
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
        <Box>
          <Typography variant="h4" component="h1" gutterBottom>
            Fee Management
          </Typography>
          <Typography variant="body1" color="text.secondary">
            Manage school fees and payment structure
          </Typography>
        </Box>
        {canManageFees && (
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
            Add Fee
          </Button>
        )}
      </Box>

      {isError && (
        <Alert
          severity="error"
          sx={{ mb: 2 }}
          action={
            <Button color="inherit" size="small" onClick={refetch}>
              Retry
            </Button>
          }
        >
          Failed to load fees
          {error?.status ? ` (HTTP ${error.status})` : ''}: {error?.data?.message || 'unexpected error'}
        </Alert>
      )}

      {/* Data Grid */}
      <Paper sx={{ height: 600, width: '100%' }}>
        <DataGrid
          rows={fees}
          columns={columns}
          loading={isLoading}
          pageSizeOptions={[10, 25, 50]}
          initialState={{
            pagination: {
              paginationModel: { pageSize: 10 },
            },
          }}
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
            {editingFee ? 'Edit Fee' : 'Add New Fee'}
          </DialogTitle>
          <DialogContent>
            <Grid container spacing={2} sx={{ mt: 1 }}>
              <Grid item xs={12} md={6}>
                <FormControl fullWidth error={!!errors.feeType}>
                  <InputLabel>Fee Type</InputLabel>
                  <Controller
                    name="feeType"
                    control={control}
                    render={({ field }) => (
                      <Select {...field} label="Fee Type">
                        <MenuItem value="TUITION">Tuition Fee</MenuItem>
                        <MenuItem value="ADMISSION">Admission Fee</MenuItem>
                        <MenuItem value="EXAMINATION">Examination Fee</MenuItem>
                        <MenuItem value="LIBRARY">Library Fee</MenuItem>
                        <MenuItem value="LABORATORY">Laboratory Fee</MenuItem>
                        <MenuItem value="TRANSPORT">Transport Fee</MenuItem>
                        <MenuItem value="HOSTEL">Hostel Fee</MenuItem>
                        <MenuItem value="MISCELLANEOUS">Miscellaneous</MenuItem>
                      </Select>
                    )}
                  />
                  {errors.feeType && (
                    <Typography variant="caption" color="error" sx={{ mt: 1, ml: 2 }}>
                      {errors.feeType?.message}
                    </Typography>
                  )}
                </FormControl>
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField
                  {...register('amount')}
                  fullWidth
                  label="Amount (₹)"
                  type="number"
                  error={!!errors.amount}
                  helperText={errors.amount?.message}
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField
                  {...register('dueDate')}
                  fullWidth
                  label="Due Date"
                  type="date"
                  InputLabelProps={{ shrink: true }}
                  error={!!errors.dueDate}
                  helperText={errors.dueDate?.message}
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField
                  {...register('academicYear')}
                  fullWidth
                  label="Academic Year"
                  placeholder="e.g., 2024-2025"
                  error={!!errors.academicYear}
                  helperText={errors.academicYear?.message}
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField
                  {...register('classGrade')}
                  fullWidth
                  label="Class Grade"
                  error={!!errors.classGrade}
                  helperText={errors.classGrade?.message}
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <FormControl fullWidth error={!!errors.isMandatory}>
                  <InputLabel>Mandatory</InputLabel>
                  <Controller
                    name="isMandatory"
                    control={control}
                    render={({ field }) => (
                      <Select {...field} label="Mandatory">
                        <MenuItem value={true}>Yes</MenuItem>
                        <MenuItem value={false}>No</MenuItem>
                      </Select>
                    )}
                  />
                  {errors.isMandatory && (
                    <Typography variant="caption" color="error" sx={{ mt: 1, ml: 2 }}>
                      {errors.isMandatory?.message}
                    </Typography>
                  )}
                </FormControl>
              </Grid>
              <Grid item xs={12}>
                <TextField
                  {...register('description')}
                  fullWidth
                  label="Description"
                  multiline
                  rows={3}
                  error={!!errors.description}
                  helperText={errors.description?.message}
                />
              </Grid>
            </Grid>
          </DialogContent>
          <DialogActions>
            <Button onClick={handleClose}>Cancel</Button>
            <Button type="submit" variant="contained">
              {editingFee ? 'Update' : 'Create'}
            </Button>
          </DialogActions>
        </form>
      </Dialog>

      {/* Assign a structure to students: creates the PENDING student-fee rows the accountant
          collects against. Students already billed for this structure are shown disabled so a
          double click cannot bill them twice. */}
      <Dialog open={Boolean(assignFee)} onClose={closeAssign} maxWidth="sm" fullWidth>
        <DialogTitle>Assign fee to students</DialogTitle>
        <DialogContent>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            {assignFee?.feeType} · ₹{Number(assignFee?.amount || 0).toLocaleString('en-IN')} ·{' '}
            {assignFee?.classGrade || 'all classes'}
            {assignFee?.dueDate ? ` · due ${dayjs(assignFee.dueDate).format('DD MMM YYYY')}` : ''}
          </Typography>
          {students.length === 0 ? (
            <Alert severity="info">No students to bill yet. Add students first.</Alert>
          ) : (
            <Box
              sx={{
                maxHeight: 360,
                overflow: 'auto',
                border: 1,
                borderColor: 'divider',
                borderRadius: 1,
                px: 1,
              }}
            >
              {students.map((student) => {
                const billed = billedStudentIds.has(student.id);
                return (
                  <FormControlLabel
                    key={student.id}
                    sx={{ display: 'flex', mr: 0 }}
                    disabled={billed}
                    control={
                      <Checkbox
                        checked={billed || assignSelection.includes(student.id)}
                        onChange={() => toggleAssign(student.id)}
                      />
                    }
                    label={`${student.firstName} ${student.lastName} · ${classroomName(student.classId)}${
                      billed ? ' · already billed' : ''
                    }`}
                  />
                );
              })}
            </Box>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={closeAssign}>Cancel</Button>
          <Button
            variant="contained"
            disabled={assigning || assignSelection.length === 0}
            onClick={handleAssign}
          >
            {assigning ? 'Assigning…' : `Assign to ${assignSelection.length} selected`}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default FeesPage;
