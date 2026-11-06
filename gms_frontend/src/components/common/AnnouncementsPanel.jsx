import { Paper, Typography, Box, Chip, LinearProgress } from '@mui/material';
import { Campaign, PushPin } from '@mui/icons-material';
import { useGetPublishedAnnouncementsQuery } from '../../store/api/apiSlice';

const PRIORITY_COLORS = {
  LOW: 'default',
  NORMAL: 'info',
  HIGH: 'warning',
  URGENT: 'error',
};

const isLive = (announcement) =>
  !announcement.expiryDate || new Date(announcement.expiryDate) > new Date();

// The audience field is a single choice, so a portal shows the entries addressed to
// everyone plus its own role — the same rule the admin form offers.
const AnnouncementsPanel = ({ audience }) => {
  const { data: announcements = [], isLoading, error } = useGetPublishedAnnouncementsQuery();

  const visible = (Array.isArray(announcements) ? announcements : [])
    .filter((announcement) => announcement.targetAudience === 'ALL' || announcement.targetAudience === audience)
    .filter(isLive)
    .sort((a, b) => {
      if (a.isPinned !== b.isPinned) return a.isPinned ? -1 : 1;
      return new Date(b.publishDate) - new Date(a.publishDate);
    });

  return (
    <Paper sx={{ p: 3 }}>
      <Typography variant="h6" gutterBottom>
        Announcements
      </Typography>
      {isLoading ? (
        <LinearProgress />
      ) : error ? (
        <Typography color="error">
          Announcements are unavailable. {error.message || 'Please try again.'}
        </Typography>
      ) : visible.length === 0 ? (
        <Typography color="text.secondary">
          No announcements have been published for you yet.
        </Typography>
      ) : (
        <Box>
          {visible.map((announcement) => (
            <Box
              key={announcement.id}
              sx={{
                display: 'flex',
                alignItems: 'flex-start',
                py: 2,
                borderBottom: '1px solid #e0e0e0',
                '&:last-child': { borderBottom: 'none' },
              }}
            >
              <Campaign sx={{ mr: 2, mt: 0.5, color: 'text.secondary' }} />
              <Box sx={{ flexGrow: 1, minWidth: 0 }}>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap' }}>
                  <Typography variant="subtitle1">{announcement.title}</Typography>
                  {announcement.isPinned && <PushPin fontSize="inherit" color="action" titleAccess="Pinned" />}
                  <Chip
                    size="small"
                    label={announcement.priority}
                    color={PRIORITY_COLORS[announcement.priority] || 'default'}
                  />
                </Box>
                <Typography variant="body2" color="text.secondary" sx={{ whiteSpace: 'pre-line' }}>
                  {announcement.content}
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  {announcement.publishDate
                    ? new Date(announcement.publishDate).toLocaleString()
                    : 'Unpublished timestamp'}
                </Typography>
              </Box>
            </Box>
          ))}
        </Box>
      )}
    </Paper>
  );
};

export default AnnouncementsPanel;
