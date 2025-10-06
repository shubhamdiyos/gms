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
  School,
} from '@mui/icons-material';
import { useForm } from 'react-hook-form';
import { yupResolver } from '@hookform/resolvers/yup';
import * as yup from 'yup';
import {
  useGetSchoolsQuery,
  useCreateSchoolMutation,
  useUpdateSchoolMutation,
  useToggleSchoolMutation,
  useGetSchoolByIdQuery,
} from '../../store/api/apiSlice';
import { toast } from 'react-toastify';

const schema = yup.object({
  schoolName: yup.string().required('School name is required'),
  schoolCode: yup.string().required('School code is required'),
  principalName: yup.string().required('Principal name is required'),
  phone: yup.string().required('Phone is required'),
  email: yup.string().email('Invalid email').required('Email is required'),
  address: yup.string().required('Address is required'),
  establishedYear: yup.number().required('Established year is required'),
  boardAffiliation: yup.string().required('Board affiliation is required'),
  // Required for school creation (admin bootstrap)
  adminFullName: yup.string().when('isEdit', {
    is: false,
    then: (schema) => schema.required('Admin full name is required'),
    otherwise: (schema) => schema.notRequired(),
  }),
  adminEmail: yup.string().email('Invalid email').when('isEdit', {
    is: false,
    then: (schema) => schema.required('Admin email is required'),
    otherwise: (schema) => schema.notRequired(),
  }),
});

const SchoolsPage = () => {
  const [open, setOpen] = useState(false);
  const [viewOpen, setViewOpen] = useState(false);
  const [editingSchool, setEditingSchool] = useState(null);
  const [viewingSchool, setViewingSchool] = useState(null);
  
  const { data: schools = [], isLoading, refetch } = useGetSchoolsQuery();
  const [createSchool] = useCreateSchoolMutation();
  const [updateSchool] = useUpdateSchoolMutation();
  const [toggleSchool] = useToggleSchoolMutation();

  const {
    register,
    handleSubmit,
    reset,
    setValue,
    formState: { errors },
  } = useForm({
    resolver: yupResolver(schema),
    context: { isEdit: !!editingSchool },
  });

  const handleOpen = (school = null) => {
    setEditingSchool(school);
    if (school) {
      reset(school);
    } else {
      reset();
    }
    setOpen(true);
  };

  const handleClose = () => {
    setOpen(false);
    setEditingSchool(null);
    reset();
  };
  
  const handleViewOpen = (school) => {
    setViewingSchool(school);
    setViewOpen(true);
  };
  
  const handleViewClose = () => {
    setViewOpen(false);
    setViewingSchool(null);
  };

  const onSubmit = async (data) => {
    try {
      if (editingSchool) {
        await updateSchool({ id: editingSchool.id, ...data }).unwrap();
        toast.success('School updated successfully');
      } else {
        await createSchool(data).unwrap();
        toast.success('School created successfully! Admin credentials sent to email.');
      }
      handleClose();
      refetch();
    } catch (error) {
      console.error('Operation error:', error);
      toast.error(error?.data?.message || 'Operation failed');
    }
  };

  const handleToggleStatus = async (school) => {
    const newStatus = school.status === '1' ? false : true;
    const action = newStatus ? 'activate' : 'deactivate';
    
    if (window.confirm(`Are you sure you want to ${action} this school?`)) {
      try {
        await toggleSchool({ id: school.id, isActive: newStatus }).unwrap();
        toast.success(`School ${action}d successfully`);
        refetch();
      } catch (error) {
        console.error('Toggle error:', error);
        toast.error(`Failed to ${action} school`);
      }
    }
  };

  const columns = [
    {
      field: 'schoolName',
      headerName: 'School Name',
      width: 200,
      renderCell: (params) => (
        <Box sx={{ display: 'flex', alignItems: 'center' }}>
          <School sx={{ mr: 1, color: '#1976d2' }} />
          {params.value}
        </Box>
      ),
    },
    {
      field: 'schoolCode',
      headerName: 'Code',
      width: 120,
    },
    {
      field: 'principalName',
      headerName: 'Principal',
      width: 180,
    },
    {
      field: 'phone',
      headerName: 'Phone',
      width: 130,
    },
    {
      field: 'email',
      headerName: 'Email',
      width: 200,
    },
    {
      field: 'boardAffiliation',
      headerName: 'Board',
      width: 120,
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
          <Tooltip title="View Details">
            <IconButton size="small" onClick={() => handleViewOpen(params.row)}>
              <Visibility />
            </IconButton>
          </Tooltip>
          <Tooltip title="Edit">
            <IconButton size="small" onClick={() => handleOpen(params.row)}>
              <Edit />
            </IconButton>
          </Tooltip>
          <Tooltip title={params.row.status === '1' ? 'Deactivate' : 'Activate'}>
            <IconButton 
              size="small" 
              onClick={() => handleToggleStatus(params.row)}
              color={params.row.status === '1' ? 'error' : 'success'}
            >
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
            Schools Management
          </Typography>
          <Typography variant="body1" color="text.secondary">
            Manage all schools in the system
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
          Add School
        </Button>
      </Box>

      {/* Data Grid */}
      <Paper sx={{ height: 600, width: '100%' }}>
        <DataGrid
          rows={schools}
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
            {editingSchool ? 'Edit School' : 'Add New School'}
          </DialogTitle>
          <DialogContent>
            <Grid container spacing={2} sx={{ mt: 1 }}>
              <Grid item xs={12} md={6}>
                <TextField
                  {...register('schoolName')}
                  fullWidth
                  label="School Name"
                  error={!!errors.schoolName}
                  helperText={errors.schoolName?.message}
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField
                  {...register('schoolCode')}
                  fullWidth
                  label="School Code"
                  error={!!errors.schoolCode}
                  helperText={errors.schoolCode?.message}
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField
                  {...register('principalName')}
                  fullWidth
                  label="Principal Name"
                  error={!!errors.principalName}
                  helperText={errors.principalName?.message}
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField
                  {...register('phone')}
                  fullWidth
                  label="Phone"
                  error={!!errors.phone}
                  helperText={errors.phone?.message}
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
                  {...register('establishedYear')}
                  fullWidth
                  label="Established Year"
                  type="number"
                  error={!!errors.establishedYear}
                  helperText={errors.establishedYear?.message}
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField
                  {...register('boardAffiliation')}
                  fullWidth
                  label="Board Affiliation"
                  error={!!errors.boardAffiliation}
                  helperText={errors.boardAffiliation?.message}
                />
              </Grid>
              <Grid item xs={12}>
                <TextField
                  {...register('address')}
                  fullWidth
                  label="Address"
                  multiline
                  rows={3}
                  error={!!errors.address}
                  helperText={errors.address?.message}
                />
              </Grid>
              
              {/* Admin Bootstrap Fields - Only for Create */}
              {!editingSchool && (
                <>
                  <Grid item xs={12}>
                    <Typography variant="h6" sx={{ mt: 2, mb: 1 }}>
                      School Admin Details
                    </Typography>
                  </Grid>
                  <Grid item xs={12} md={6}>
                    <TextField
                      {...register('adminFullName')}
                      fullWidth
                      label="Admin Full Name"
                      error={!!errors.adminFullName}
                      helperText={errors.adminFullName?.message}
                    />
                  </Grid>
                  <Grid item xs={12} md={6}>
                    <TextField
                      {...register('adminEmail')}
                      fullWidth
                      label="Admin Email"
                      type="email"
                      error={!!errors.adminEmail}
                      helperText={errors.adminEmail?.message || 'Login credentials will be sent to this email'}
                    />
                  </Grid>
                </>
              )}
            </Grid>
          </DialogContent>
          <DialogActions>
            <Button onClick={handleClose}>Cancel</Button>
            <Button type="submit" variant="contained">
              {editingSchool ? 'Update' : 'Create'}
            </Button>
          </DialogActions>
        </form>
      </Dialog>
      
      {/* View School Dialog */}
      <Dialog open={viewOpen} onClose={handleViewClose} maxWidth="md" fullWidth>
        <DialogTitle>
          School Details - {viewingSchool?.schoolName}
        </DialogTitle>
        <DialogContent>
          {viewingSchool && (
            <Grid container spacing={2} sx={{ mt: 1 }}>
              <Grid item xs={12} md={6}>
                <Typography variant="subtitle2" color="text.secondary">
                  School Name
                </Typography>
                <Typography variant="body1" sx={{ mb: 2 }}>
                  {viewingSchool.schoolName}
                </Typography>
              </Grid>
              <Grid item xs={12} md={6}>
                <Typography variant="subtitle2" color="text.secondary">
                  School Code
                </Typography>
                <Typography variant="body1" sx={{ mb: 2 }}>
                  {viewingSchool.schoolCode}
                </Typography>
              </Grid>
              <Grid item xs={12} md={6}>
                <Typography variant="subtitle2" color="text.secondary">
                  Principal Name
                </Typography>
                <Typography variant="body1" sx={{ mb: 2 }}>
                  {viewingSchool.principalName}
                </Typography>
              </Grid>
              <Grid item xs={12} md={6}>
                <Typography variant="subtitle2" color="text.secondary">
                  Phone
                </Typography>
                <Typography variant="body1" sx={{ mb: 2 }}>
                  {viewingSchool.phone}
                </Typography>
              </Grid>
              <Grid item xs={12} md={6}>
                <Typography variant="subtitle2" color="text.secondary">
                  Email
                </Typography>
                <Typography variant="body1" sx={{ mb: 2 }}>
                  {viewingSchool.email}
                </Typography>
              </Grid>
              <Grid item xs={12} md={6}>
                <Typography variant="subtitle2" color="text.secondary">
                  Established Year
                </Typography>
                <Typography variant="body1" sx={{ mb: 2 }}>
                  {viewingSchool.establishedYear}
                </Typography>
              </Grid>
              <Grid item xs={12} md={6}>
                <Typography variant="subtitle2" color="text.secondary">
                  Board Affiliation
                </Typography>
                <Typography variant="body1" sx={{ mb: 2 }}>
                  {viewingSchool.boardAffiliation}
                </Typography>
              </Grid>
              <Grid item xs={12} md={6}>
                <Typography variant="subtitle2" color="text.secondary">
                  Status
                </Typography>
                <Chip
                  label={viewingSchool.status === '1' ? 'Active' : 'Inactive'}
                  color={viewingSchool.status === '1' ? 'success' : 'error'}
                  size="small"
                  sx={{ mb: 2 }}
                />
              </Grid>
              <Grid item xs={12}>
                <Typography variant="subtitle2" color="text.secondary">
                  Address
                </Typography>
                <Typography variant="body1" sx={{ mb: 2 }}>
                  {viewingSchool.address}
                </Typography>
              </Grid>
              <Grid item xs={12} md={6}>
                <Typography variant="subtitle2" color="text.secondary">
                  Created At
                </Typography>
                <Typography variant="body1" sx={{ mb: 2 }}>
                  {new Date(viewingSchool.createdAt).toLocaleDateString()}
                </Typography>
              </Grid>
              <Grid item xs={12} md={6}>
                <Typography variant="subtitle2" color="text.secondary">
                  Updated At
                </Typography>
                <Typography variant="body1" sx={{ mb: 2 }}>
                  {new Date(viewingSchool.updatedAt).toLocaleDateString()}
                </Typography>
              </Grid>
            </Grid>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={handleViewClose}>Close</Button>
          <Button 
            variant="contained" 
            onClick={() => {
              handleViewClose();
              handleOpen(viewingSchool);
            }}
          >
            Edit School
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default SchoolsPage;
