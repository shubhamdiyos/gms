import { Component } from 'react';
import { Box, Button, Paper, Typography } from '@mui/material';

/**
 * A render crash must cost one screen, not the whole portal.
 *
 * <p>There was no boundary anywhere in this SPA, so a single throw during render unmounted
 * the entire route tree and left the user looking at a blank page with no way back. The
 * defect that proved it was the empty-collection contract: a `204` answer became `null` in
 * the request layer, `data: classrooms = []` did not default on `null`, and `classrooms.map`
 * threw on the students page — one page's data shape emptied every page behind it.</p>
 *
 * <p>The boundary catches render and lifecycle errors of its descendants. It does not catch
 * errors in event handlers or in promise callbacks; those belong to the code that raises
 * them, and a boundary that swallowed them would hide the defect rather than contain it.</p>
 */
class ErrorBoundary extends Component {
  constructor(props) {
    super(props);
    this.state = { error: null };
    this.handleReset = this.handleReset.bind(this);
  }

  static getDerivedStateFromError(error) {
    return { error };
  }

  componentDidCatch(error, info) {
    // eslint-disable-next-line no-console
    console.error('A screen failed to render', error, info);
  }

  handleReset() {
    this.setState({ error: null });
  }

  render() {
    const { error } = this.state;
    const { children } = this.props;

    if (!error) {
      return children;
    }

    return (
      <Box
        sx={{
          minHeight: '100vh',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          bgcolor: 'background.default',
          p: 2,
        }}
      >
        <Paper sx={{ p: 4, maxWidth: 560, width: '100%' }}>
          <Typography variant="h5" component="h1" gutterBottom>
            This screen could not be displayed
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
            The rest of the portal still works. Try the screen again, or go back to your
            dashboard.
          </Typography>
          <Typography
            variant="caption"
            color="text.secondary"
            component="pre"
            sx={{ whiteSpace: 'pre-wrap', mb: 3 }}
          >
            {error.message}
          </Typography>
          <Box sx={{ display: 'flex', gap: 2 }}>
            <Button variant="contained" onClick={this.handleReset}>
              Try again
            </Button>
            <Button variant="outlined" onClick={() => window.location.assign('/')}>
              Back to dashboard
            </Button>
          </Box>
        </Paper>
      </Box>
    );
  }
}

export default ErrorBoundary;
