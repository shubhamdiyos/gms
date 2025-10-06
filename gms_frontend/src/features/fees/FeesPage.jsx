import { useState } from 'react';
import {
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
  AttachMoney,
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
} from '../../store/api/apiSlice';
import { toast } from 'react-toastify';
import dayjs from 'dayjs';

const schema = yup.object({
  feeName: yup.string().required('Fee name is required'),
  amount: yup.number().positive('Amount must be positive').required('Amount is required'),
  feeType: yup.string().required('Fee type is required'),
  dueDate: yup.date().required('Due date is required'),
  classroomId: yup.number().required('Classroom is required'),
  description: yup.string(),
});

const FeesPage = () => {
  const [open, setOpen] = useState(false);
  const [editingFee, setEditingFee] = useState(null);
  
  const { data: fees = [], isLoading, refetch } = useGetFeesQuery();
  const { data: classrooms = [] } = useGetClassroomsQuery();
  const [createFee] = useCreateFeeMutation();
  const [updateFee] = useUpdateFeeMutation();
  const [deleteFee] = useDeleteFeeMutation();

  const {
    register,
    handleSubmit,
    reset,
    control,
    formState: { errors },
  } = useForm({
    resolver: yupResolver(schema),
  });

  const handleOpen = (fee = null) => {
    setEditingFee(fee);
    if (fee) {
      reset({
        ...fee,
        dueDate: fee.dueDate ? new Date(fee.dueDate).toISOString().split('T')[0] : '',
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
    try {
      if (editingFee) {
        await updateFee({ id: editingFee.id, ...data }).unwrap();
        toast.success('Fee updated successfully');
      } else {
        await createFee(data).unwrap();
        toast.success('Fee created successfully');
      }
      handleClose();
      refetch();
    } catch (error) {
      toast.error('Operation failed');
    }
  };

  const handleDelete = async (id) => {
    if (window.confirm('Are you sure you want to delete this fee?')) {
      try {
        await deleteFee(id).unwrap();
        toast.success('Fee deleted successfully');
        refetch();
      } catch (error) {
        toast.error('Delete failed');
      }
    }
  };

  const getClassroomName = (classroomId) => {
    const classroom = classrooms.find(c => c.id === classroomId);
    return classroom ? classroom.className : 'Unknown';
  };

  const columns = [
    {
      field: 'feeName',
      headerName: 'Fee Name',
      width: 200,
      renderCell: (params) => (
        <Box sx={{ display: 'flex', alignItems: 'center' }}>
          <AttachMoney sx={{ mr: 1, color: '#4caf50' }} />
          {params.value}
        </Box>
      ),
    },
    {
      field: 'feeType',
      headerName: 'Type',
      width: 120,
      renderCell: (params) => (
        <Chip
          label={params.value}
          color="primary"
          variant="outlined"
          size="small"
        />
      ),
    },
    {
      field: 'amount',
      headerName: 'Amount',
      width: 120,
      renderCell: (params) => `₹${params.value?.toLocaleString()}`,
    },
    {
      field: 'classroomId',
      headerName: 'Class',
      width: 120,
      renderCell: (params) => getClassroomName(params.value),
    },
    {
      field: 'dueDate',
      headerName: 'Due Date',
      width: 130,
      renderCell: (params) => dayjs(params.value).format('MMM DD, YYYY'),
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
            Fee Management
          </Typography>
          <Typography variant="body1" color="text.secondary">
            Manage school fees and payment structure
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
          Add Fee
        </Button>
      </Box>

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
                <TextField
                  {...register('feeName')}
                  fullWidth
                  label="Fee Name"
                  error={!!errors.feeName}
                  helperText={errors.feeName?.message}
                />
              </Grid>
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
              <Grid item xs={12}>
                <FormControl fullWidth error={!!errors.classroomId}>
                  <InputLabel>Classroom</InputLabel>
                  <Controller
                    name="classroomId"
                    control={control}
                    render={({ field }) => (
                      <Select {...field} label="Classroom">
                        {classrooms.map((classroom) => (
                          <MenuItem key={classroom.id} value={classroom.id}>
                            {classroom.className} - {classroom.section}
                          </MenuItem>
                        ))}
                      </Select>
                    )}
                  />
                  {errors.classroomId && (
                    <Typography variant="caption" color="error" sx={{ mt: 1, ml: 2 }}>
                      {errors.classroomId?.message}
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
    </Box>
  );
};

export default FeesPage;
