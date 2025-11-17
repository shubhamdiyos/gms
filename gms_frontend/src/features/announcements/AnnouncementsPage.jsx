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
  Campaign,
  PushPin,
  Publish,
  Unpublished,
} from '@mui/icons-material';
import { useForm, Controller } from 'react-hook-form';
import { yupResolver } from '@hookform/resolvers/yup';
import * as yup from 'yup';
import {
  useGetAnnouncementsQuery,
  useCreateAnnouncementMutation,
  useUpdateAnnouncementMutation,
  useDeleteAnnouncementMutation,
  usePublishAnnouncementMutation,
  useUnpublishAnnouncementMutation,
} from '../../store/api/apiSlice';
import { toast } from 'react-toastify';
import dayjs from 'dayjs';

const schema = yup.object({
  title: yup.string().required('Title is required'),
  content: yup.string().required('Content is required'),
  announcementType: yup.string().required('Type is required'),
  targetAudience: yup.string().required('Target audience is required'),
  publishDate: yup.string().required('Publish date is required'),
  expiryDate: yup.string().nullable(),
  priority: yup.string().required('Priority is required'),
  category: yup.string().required('Category is required'),
  clickActionUrl: yup.string().url('Please enter a valid URL').nullable().optional(),
  isPinned: yup.boolean().required(),
});

const ANNOUNCEMENT_TYPES = ['GENERAL', 'URGENT', 'HOLIDAY', 'EVENT'];
const TARGET_AUDIENCES = ['ALL', 'STUDENTS', 'PARENTS', 'TEACHERS', 'STAFF'];
const PRIORITIES = ['LOW', 'NORMAL', 'HIGH', 'URGENT'];
const CATEGORIES = ['NEWS', 'EVENT', 'NOTICE', 'ALERT'];

const AnnouncementsPage = () => {
  const [open, setOpen] = useState(false);
  const [editingAnnouncement, setEditingAnnouncement] = useState(null);

  const {
    data: announcements = [],
    isLoading,
    refetch,
  } = useGetAnnouncementsQuery();

  const [createAnnouncement] = useCreateAnnouncementMutation();
  const [updateAnnouncement] = useUpdateAnnouncementMutation();
  const [deleteAnnouncement] = useDeleteAnnouncementMutation();
  const [publishAnnouncement] = usePublishAnnouncementMutation();
  const [unpublishAnnouncement] = useUnpublishAnnouncementMutation();

  const {
    register,
    handleSubmit,
    reset,
    control,
    formState: { errors },
  } = useForm({
    resolver: yupResolver(schema),
    defaultValues: {
      title: '',
      content: '',
      announcementType: 'GENERAL',
      targetAudience: 'ALL',
      publishDate: dayjs().format('YYYY-MM-DDTHH:mm'),
      expiryDate: '',
      priority: 'NORMAL',
      category: 'NOTICE',
      clickActionUrl: '',
      isPinned: false,
    },
  });

  const handleOpen = (announcement = null) => {
    setEditingAnnouncement(announcement);
    if (announcement) {
      reset({
        title: announcement.title || '',
        content: announcement.content || '',
        announcementType: announcement.announcementType || 'GENERAL',
        targetAudience: announcement.targetAudience || 'ALL',
        publishDate: announcement.publishDate
          ? dayjs(announcement.publishDate).format('YYYY-MM-DDTHH:mm')
          : dayjs().format('YYYY-MM-DDTHH:mm'),
        expiryDate: announcement.expiryDate
          ? dayjs(announcement.expiryDate).format('YYYY-MM-DDTHH:mm')
          : '',
        priority: announcement.priority || 'NORMAL',
        category: announcement.category || 'NOTICE',
        clickActionUrl: announcement.clickActionUrl || '',
        isPinned: announcement.isPinned ?? false,
      });
    } else {
      reset({
        title: '',
        content: '',
        announcementType: 'GENERAL',
        targetAudience: 'ALL',
        publishDate: dayjs().format('YYYY-MM-DDTHH:mm'),
        expiryDate: '',
        priority: 'NORMAL',
        category: 'NOTICE',
        clickActionUrl: '',
        isPinned: false,
      });
    }
    setOpen(true);
  };

  const handleClose = () => {
    setOpen(false);
    setEditingAnnouncement(null);
  };

  const onSubmit = async (data) => {
    try {
      const payload = {
        ...data,
        // Convert empty expiryDate to null
        expiryDate: data.expiryDate || null,
      };

      if (editingAnnouncement) {
        await updateAnnouncement({ id: editingAnnouncement.id, ...payload }).unwrap();
        toast.success('Announcement updated successfully');
      } else {
        await createAnnouncement(payload).unwrap();
        toast.success('Announcement created successfully');
      }

      handleClose();
      refetch();
    } catch (error) {
      toast.error('Operation failed');
    }
  };

  const handleDelete = async (id) => {
    if (window.confirm('Are you sure you want to delete this announcement?')) {
      try {
        await deleteAnnouncement(id).unwrap();
        toast.success('Announcement deleted successfully');
        refetch();
      } catch (error) {
        toast.error('Delete failed');
      }
    }
  };

  const handlePublishToggle = async (announcement) => {
    try {
      if (announcement.isPublished) {
        await unpublishAnnouncement(announcement.id).unwrap();
        toast.success('Announcement unpublished');
      } else {
        await publishAnnouncement(announcement.id).unwrap();
        toast.success('Announcement published');
      }
      refetch();
    } catch (error) {
      toast.error('Failed to update publish status');
    }
  };

  const columns = [
    {
      field: 'title',
      headerName: 'Title',
      width: 220,
      renderCell: (params) => (
        <Box sx={{ display: 'flex', alignItems: 'center' }}>
          <Campaign sx={{ mr: 1, color: '#1976d2' }} />
          {params.value}
        </Box>
      ),
    },
    {
      field: 'announcementType',
      headerName: 'Type',
      width: 120,
      renderCell: (params) => (
        <Chip
          label={params.value}
          color={params.value === 'URGENT' || params.value === 'HOLIDAY' ? 'error' : 'primary'}
          size="small"
          variant="outlined"
        />
      ),
    },
    {
      field: 'targetAudience',
      headerName: 'Audience',
      width: 130,
      renderCell: (params) => (
        <Chip label={params.value} color="secondary" size="small" variant="outlined" />
      ),
    },
    {
      field: 'priority',
      headerName: 'Priority',
      width: 110,
      renderCell: (params) => {
        const value = params.value || 'NORMAL';
        let color = 'default';
        if (value === 'HIGH' || value === 'URGENT') color = 'error';
        else if (value === 'NORMAL') color = 'primary';
        else if (value === 'LOW') color = 'default';
        return <Chip label={value} color={color} size="small" />;
      },
    },
    {
      field: 'publishDate',
      headerName: 'Publish Date',
      width: 170,
      renderCell: (params) =>
        params.value ? dayjs(params.value).format('MMM DD, YYYY HH:mm') : '-',
    },
    {
      field: 'isPublished',
      headerName: 'Status',
      width: 120,
      renderCell: (params) => (
        <Chip
          label={params.value ? 'Published' : 'Draft'}
          color={params.value ? 'success' : 'warning'}
          size="small"
        />
      ),
    },
    {
      field: 'isPinned',
      headerName: 'Pinned',
      width: 90,
      renderCell: (params) => (
        <Chip
          icon={<PushPin sx={{ fontSize: 16 }} />}
          label={params.value ? 'Yes' : 'No'}
          size="small"
          variant={params.value ? 'filled' : 'outlined'}
          color={params.value ? 'secondary' : 'default'}
        />
      ),
    },
    {
      field: 'actions',
      headerName: 'Actions',
      width: 200,
      sortable: false,
      renderCell: (params) => (
        <Box>
          <Tooltip title="Edit">
            <IconButton size="small" onClick={() => handleOpen(params.row)}>
              <Edit />
            </IconButton>
          </Tooltip>
          <Tooltip title={params.row.isPublished ? 'Unpublish' : 'Publish'}>
            <IconButton size="small" onClick={() => handlePublishToggle(params.row)}>
              {params.row.isPublished ? <Unpublished /> : <Publish />}
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
            Announcements
          </Typography>
          <Typography variant="body1" color="text.secondary">
            Manage school-wide announcements for students, parents, and staff.
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
          Add Announcement
        </Button>
      </Box>

      {/* Data Grid */}
      <Paper sx={{ height: 600, width: '100%' }}>
        <DataGrid
          rows={announcements}
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
          <DialogTitle>{editingAnnouncement ? 'Edit Announcement' : 'Add New Announcement'}</DialogTitle>
          <DialogContent>
            <Grid container spacing={2} sx={{ mt: 1 }}>
              <Grid item xs={12} md={6}>
                <TextField
                  {...register('title')}
                  fullWidth
                  label="Title"
                  error={!!errors.title}
                  helperText={errors.title?.message}
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <FormControl fullWidth error={!!errors.announcementType}>
                  <InputLabel>Type</InputLabel>
                  <Controller
                    name="announcementType"
                    control={control}
                    render={({ field }) => (
                      <Select {...field} label="Type">
                        {ANNOUNCEMENT_TYPES.map((type) => (
                          <MenuItem key={type} value={type}>
                            {type}
                          </MenuItem>
                        ))}
                      </Select>
                    )}
                  />
                  {errors.announcementType && (
                    <Typography variant="caption" color="error" sx={{ mt: 1, ml: 2 }}>
                      {errors.announcementType?.message}
                    </Typography>
                  )}
                </FormControl>
              </Grid>
              <Grid item xs={12} md={6}>
                <FormControl fullWidth error={!!errors.targetAudience}>
                  <InputLabel>Target Audience</InputLabel>
                  <Controller
                    name="targetAudience"
                    control={control}
                    render={({ field }) => (
                      <Select {...field} label="Target Audience">
                        {TARGET_AUDIENCES.map((audience) => (
                          <MenuItem key={audience} value={audience}>
                            {audience}
                          </MenuItem>
                        ))}
                      </Select>
                    )}
                  />
                  {errors.targetAudience && (
                    <Typography variant="caption" color="error" sx={{ mt: 1, ml: 2 }}>
                      {errors.targetAudience?.message}
                    </Typography>
                  )}
                </FormControl>
              </Grid>
              <Grid item xs={12} md={6}>
                <FormControl fullWidth error={!!errors.priority}>
                  <InputLabel>Priority</InputLabel>
                  <Controller
                    name="priority"
                    control={control}
                    render={({ field }) => (
                      <Select {...field} label="Priority">
                        {PRIORITIES.map((priority) => (
                          <MenuItem key={priority} value={priority}>
                            {priority}
                          </MenuItem>
                        ))}
                      </Select>
                    )}
                  />
                  {errors.priority && (
                    <Typography variant="caption" color="error" sx={{ mt: 1, ml: 2 }}>
                      {errors.priority?.message}
                    </Typography>
                  )}
                </FormControl>
              </Grid>
              <Grid item xs={12} md={6}>
                <FormControl fullWidth error={!!errors.category}>
                  <InputLabel>Category</InputLabel>
                  <Controller
                    name="category"
                    control={control}
                    render={({ field }) => (
                      <Select {...field} label="Category">
                        {CATEGORIES.map((category) => (
                          <MenuItem key={category} value={category}>
                            {category}
                          </MenuItem>
                        ))}
                      </Select>
                    )}
                  />
                  {errors.category && (
                    <Typography variant="caption" color="error" sx={{ mt: 1, ml: 2 }}>
                      {errors.category?.message}
                    </Typography>
                  )}
                </FormControl>
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField
                  {...register('publishDate')}
                  fullWidth
                  label="Publish Date"
                  type="datetime-local"
                  InputLabelProps={{ shrink: true }}
                  error={!!errors.publishDate}
                  helperText={errors.publishDate?.message}
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField
                  {...register('expiryDate')}
                  fullWidth
                  label="Expiry Date"
                  type="datetime-local"
                  InputLabelProps={{ shrink: true }}
                  error={!!errors.expiryDate}
                  helperText={errors.expiryDate?.message}
                />
              </Grid>
              <Grid item xs={12}>
                <TextField
                  {...register('content')}
                  fullWidth
                  label="Content"
                  multiline
                  minRows={4}
                  error={!!errors.content}
                  helperText={errors.content?.message}
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField
                  {...register('clickActionUrl')}
                  fullWidth
                  label="Click Action URL (optional)"
                  error={!!errors.clickActionUrl}
                  helperText={errors.clickActionUrl?.message}
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <FormControl fullWidth>
                  <InputLabel>Pinned</InputLabel>
                  <Controller
                    name="isPinned"
                    control={control}
                    render={({ field }) => (
                      <Select
                        {...field}
                        label="Pinned"
                        value={field.value ? 'true' : 'false'}
                        onChange={(e) => field.onChange(e.target.value === 'true')}
                      >
                        <MenuItem value="false">No</MenuItem>
                        <MenuItem value="true">Yes</MenuItem>
                      </Select>
                    )}
                  />
                </FormControl>
              </Grid>
            </Grid>
          </DialogContent>
          <DialogActions>
            <Button onClick={handleClose}>Cancel</Button>
            <Button type="submit" variant="contained">
              {editingAnnouncement ? 'Update' : 'Create'}
            </Button>
          </DialogActions>
        </form>
      </Dialog>
    </Box>
  );
};

export default AnnouncementsPage;
