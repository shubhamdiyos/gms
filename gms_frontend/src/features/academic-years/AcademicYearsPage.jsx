import { useState } from 'react';
import {
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
  FormControl,
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
  CalendarToday,
} from '@mui/icons-material';
import { useForm, Controller } from 'react-hook-form';
import { yupResolver } from '@hookform/resolvers/yup';
import * as yup from 'yup';
import {
  useGetAcademicYearsQuery,
  useCreateAcademicYearMutation,
  useUpdateAcademicYearMutation,
  useDeleteAcademicYearMutation,
} from '../../store/api/apiSlice';
import { toast } from 'react-toastify';

const schema = yup.object({
  name: yup.string().required('Year name is required'),
  startDate: yup
    .string()
    .matches(/^\d{4}-\d{2}-\d{2}$/, 'Start date is required')
    .required('Start date is required'),
  endDate: yup
    .string()
    .matches(/^\d{4}-\d{2}-\d{2}$/, 'End date is required')
    .required('End date is required')
    .test('is-after-start', 'End date must be after start date', function (value) {
      const { startDate } = this.parent;
      return !value || !startDate || value >= startDate;
    }),
  current: yup.boolean().defined(),
});

const AcademicYearsPage = () => {
  const [open, setOpen] = useState(false);
  const [editingYear, setEditingYear] = useState(null);

  const { data: academicYears = [], isLoading, refetch } = useGetAcademicYearsQuery();
  const [createAcademicYear] = useCreateAcademicYearMutation();
  const [updateAcademicYear] = useUpdateAcademicYearMutation();
  const [deleteAcademicYear] = useDeleteAcademicYearMutation();

  const {
    register,
    handleSubmit,
    reset,
    control,
    formState: { errors },
  } = useForm({
    defaultValues: { current: false },
    resolver: yupResolver(schema),
  });

  const handleOpen = (year = null) => {
    setEditingYear(year);
    if (year) {
      reset({
        ...year,
        startDate: year.startDate ? String(year.startDate).split('T')[0] : '',
        endDate: year.endDate ? String(year.endDate).split('T')[0] : '',
        current: Boolean(year.current),
      });
    } else {
      reset();
    }
    setOpen(true);
  };

  const handleClose = () => {
    setOpen(false);
    setEditingYear(null);
    reset();
  };

  const onSubmit = async (data) => {
    const payload = {
      name: data.name,
      startDate: data.startDate,
      endDate: data.endDate,
      current: data.current,
    };
    try {
      if (editingYear) {
        await updateAcademicYear({ id: editingYear.id, ...payload }).unwrap();
        toast.success('Academic year updated successfully');
      } else {
        await createAcademicYear(payload).unwrap();
        toast.success('Academic year created successfully');
      }
      handleClose();
      refetch();
    } catch {
      toast.error('Operation failed');
    }
  };

  const handleDelete = async (id) => {
    if (window.confirm('Are you sure you want to delete this academic year?')) {
      try {
        await deleteAcademicYear(id).unwrap();
        toast.success('Academic year deleted successfully');
        refetch();
      } catch {
        toast.error('Delete failed');
      }
    }
  };

  const formatDate = (dateString) => {
    if (!dateString) return '';
    return new Date(dateString).toLocaleDateString();
  };

  const columns = [
    {
      field: 'name',
      headerName: 'Academic Year',
      width: 160,
      renderCell: (params) => (
        <Box sx={{ display: 'flex', alignItems: 'center' }}>
          <CalendarToday sx={{ mr: 1, color: '#1976d2' }} />
          {params.value}
        </Box>
      ),
    },
    {
      field: 'startDate',
      headerName: 'Start Date',
      width: 130,
      renderCell: (params) => formatDate(params.value),
    },
    {
      field: 'endDate',
      headerName: 'End Date',
      width: 130,
      renderCell: (params) => formatDate(params.value),
    },
    {
      field: 'current',
      headerName: 'Current',
      width: 110,
      renderCell: (params) => (
        <Chip
          label={params.value ? 'Current' : 'Not Current'}
          color={params.value ? 'success' : 'default'}
          size="small"
        />
      ),
    },
    {
      field: 'status',
      headerName: 'Status',
      width: 100,
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
      width: 150,
      sortable: false,
      renderCell: (params) => (
        <Box>
          <Tooltip title="View">
            <IconButton size="small">
              <Visibility />
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
  ];

  return (
    <Box>
      {/* Header */}
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
        <Box>
          <Typography variant="h4" component="h1" gutterBottom>
            Academic Years Management
          </Typography>
          <Typography variant="body1" color="text.secondary">
            Manage academic year periods and sessions
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
          Add Academic Year
        </Button>
      </Box>

      {/* Data Grid */}
      <Paper sx={{ height: 600, width: '100%' }}>
        <DataGrid
          rows={academicYears}
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
      <Dialog open={open} onClose={handleClose} maxWidth="sm" fullWidth>
        <form onSubmit={handleSubmit(onSubmit)}>
          <DialogTitle>
            {editingYear ? 'Edit Academic Year' : 'Add New Academic Year'}
          </DialogTitle>
          <DialogContent>
            <Grid container spacing={2} sx={{ mt: 1 }}>
              <Grid item xs={12}>
                <TextField
                  {...register('name')}
                  fullWidth
                  label="Academic Year Name"
                  placeholder="e.g., 2024-2025"
                  error={!!errors.name}
                  helperText={errors.name?.message}
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField
                  {...register('startDate')}
                  fullWidth
                  label="Start Date"
                  type="date"
                  InputLabelProps={{ shrink: true }}
                  error={!!errors.startDate}
                  helperText={errors.startDate?.message}
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField
                  {...register('endDate')}
                  fullWidth
                  label="End Date"
                  type="date"
                  InputLabelProps={{ shrink: true }}
                  error={!!errors.endDate}
                  helperText={errors.endDate?.message}
                />
              </Grid>
              <Grid item xs={12}>
                <FormControl fullWidth error={!!errors.current}>
                  <InputLabel>Current Academic Year</InputLabel>
                  <Controller
                    name="current"
                    control={control}
                    render={({ field }) => (
                      <Select {...field} label="Current Academic Year">
                        <MenuItem value={true}>Yes</MenuItem>
                        <MenuItem value={false}>No</MenuItem>
                      </Select>
                    )}
                  />
                  {errors.current && (
                    <Typography variant="caption" color="error" sx={{ mt: 1, ml: 2 }}>
                      {errors.current?.message}
                    </Typography>
                  )}
                </FormControl>
              </Grid>
            </Grid>
          </DialogContent>
          <DialogActions>
            <Button onClick={handleClose}>Cancel</Button>
            <Button type="submit" variant="contained">
              {editingYear ? 'Update' : 'Create'}
            </Button>
          </DialogActions>
        </form>
      </Dialog>
    </Box>
  );
};

export default AcademicYearsPage;
