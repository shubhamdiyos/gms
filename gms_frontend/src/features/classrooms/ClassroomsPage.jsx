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
} from '@mui/material';
import { DataGrid } from '@mui/x-data-grid';
import {
  Add,
  Edit,
  Delete,
  Visibility,
  Class,
} from '@mui/icons-material';
import { useForm } from 'react-hook-form';
import { yupResolver } from '@hookform/resolvers/yup';
import * as yup from 'yup';
import {
  useGetClassroomsQuery,
  useCreateClassroomMutation,
  useUpdateClassroomMutation,
  useDeleteClassroomMutation,
} from '../../store/api/apiSlice';
import { toast } from 'react-toastify';

const schema = yup.object({
  className: yup.string().required('Class name is required'),
  section: yup.string().required('Section is required'),
  capacity: yup.number().positive('Capacity must be positive').required('Capacity is required'),
  roomNumber: yup.string().required('Room number is required'),
  description: yup.string(),
});

const ClassroomsPage = () => {
  const [open, setOpen] = useState(false);
  const [editingClassroom, setEditingClassroom] = useState(null);
  
  const { data: classrooms = [], isLoading, refetch } = useGetClassroomsQuery();
  const [createClassroom] = useCreateClassroomMutation();
  const [updateClassroom] = useUpdateClassroomMutation();
  const [deleteClassroom] = useDeleteClassroomMutation();

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm({
    resolver: yupResolver(schema),
  });

  const handleOpen = (classroom = null) => {
    setEditingClassroom(classroom);
    if (classroom) {
      reset(classroom);
    } else {
      reset();
    }
    setOpen(true);
  };

  const handleClose = () => {
    setOpen(false);
    setEditingClassroom(null);
    reset();
  };

  const onSubmit = async (data) => {
    try {
      if (editingClassroom) {
        await updateClassroom({ id: editingClassroom.id, ...data }).unwrap();
        toast.success('Classroom updated successfully');
      } else {
        await createClassroom(data).unwrap();
        toast.success('Classroom created successfully');
      }
      handleClose();
      refetch();
    } catch (error) {
      toast.error('Operation failed');
    }
  };

  const handleDelete = async (id) => {
    if (window.confirm('Are you sure you want to delete this classroom?')) {
      try {
        await deleteClassroom(id).unwrap();
        toast.success('Classroom deleted successfully');
        refetch();
      } catch (error) {
        toast.error('Delete failed');
      }
    }
  };

  const columns = [
    {
      field: 'className',
      headerName: 'Class Name',
      width: 150,
      renderCell: (params) => (
        <Box sx={{ display: 'flex', alignItems: 'center' }}>
          <Class sx={{ mr: 1, color: '#f57c00' }} />
          {params.value}
        </Box>
      ),
    },
    {
      field: 'section',
      headerName: 'Section',
      width: 100,
    },
    {
      field: 'roomNumber',
      headerName: 'Room Number',
      width: 130,
    },
    {
      field: 'capacity',
      headerName: 'Capacity',
      width: 100,
      renderCell: (params) => `${params.value} students`,
    },
    {
      field: 'currentStrength',
      headerName: 'Current Strength',
      width: 150,
      renderCell: (params) => `${params.value || 0} students`,
    },
    {
      field: 'description',
      headerName: 'Description',
      width: 200,
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
            Classrooms Management
          </Typography>
          <Typography variant="body1" color="text.secondary">
            Manage classroom setup and capacity
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
          Add Classroom
        </Button>
      </Box>

      {/* Data Grid */}
      <Paper sx={{ height: 600, width: '100%' }}>
        <DataGrid
          rows={classrooms}
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
            {editingClassroom ? 'Edit Classroom' : 'Add New Classroom'}
          </DialogTitle>
          <DialogContent>
            <Grid container spacing={2} sx={{ mt: 1 }}>
              <Grid item xs={12} md={6}>
                <TextField
                  {...register('className')}
                  fullWidth
                  label="Class Name"
                  error={!!errors.className}
                  helperText={errors.className?.message}
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField
                  {...register('section')}
                  fullWidth
                  label="Section"
                  error={!!errors.section}
                  helperText={errors.section?.message}
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField
                  {...register('roomNumber')}
                  fullWidth
                  label="Room Number"
                  error={!!errors.roomNumber}
                  helperText={errors.roomNumber?.message}
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField
                  {...register('capacity')}
                  fullWidth
                  label="Capacity"
                  type="number"
                  error={!!errors.capacity}
                  helperText={errors.capacity?.message}
                />
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
              {editingClassroom ? 'Update' : 'Create'}
            </Button>
          </DialogActions>
        </form>
      </Dialog>
    </Box>
  );
};

export default ClassroomsPage;
