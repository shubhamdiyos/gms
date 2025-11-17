import { Box, Typography, Paper, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, Chip } from '@mui/material';
import { Assessment } from '@mui/icons-material';
import { useGetMyResultsQuery } from '../../store/api/apiSlice';
import dayjs from 'dayjs';

const StudentResultsPage = () => {
  const { data: results = [], isLoading } = useGetMyResultsQuery();

  return (
    <Box>
      {/* Header */}
      <Box sx={{ mb: 4 }}>
        <Typography variant="h4" component="h1" gutterBottom>
          My Results
        </Typography>
        <Typography variant="body1" color="text.secondary">
          View your exam results and grades.
        </Typography>
      </Box>

      <Paper>
        <Box sx={{ p: 2, borderBottom: '1px solid #e0e0e0', display: 'flex', alignItems: 'center' }}>
          <Assessment sx={{ mr: 1, color: 'primary.main' }} />
          <Typography variant="h6">Exam Results</Typography>
        </Box>

        {isLoading ? (
          <Box sx={{ p: 4, textAlign: 'center' }}>
            <Typography>Loading results...</Typography>
          </Box>
        ) : results.length === 0 ? (
          <Box sx={{ p: 4, textAlign: 'center' }}>
            <Typography variant="body1" color="text.secondary">
              No results available yet.
            </Typography>
          </Box>
        ) : (
          <TableContainer>
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell>Exam</TableCell>
                  <TableCell>Subject</TableCell>
                  <TableCell>Marks</TableCell>
                  <TableCell>Grade</TableCell>
                  <TableCell>Percentage</TableCell>
                  <TableCell>Status</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {results.map((result) => (
                  <TableRow key={result.id}>
                    <TableCell>{result.examName || 'Exam'}</TableCell>
                    <TableCell>{result.subjectName || 'Subject'}</TableCell>
                    <TableCell>{result.obtainedMarks}</TableCell>
                    <TableCell>
                      <Chip
                        label={result.grade}
                        color={result.grade?.startsWith('A') ? 'success' : 'primary'}
                        size="small"
                      />
                    </TableCell>
                    <TableCell>{result.percentage != null ? `${result.percentage}%` : '-'}</TableCell>
                    <TableCell>
                      <Chip
                        label={result.status === '1' ? 'Published' : 'Draft'}
                        color={result.status === '1' ? 'success' : 'warning'}
                        size="small"
                      />
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </Paper>
    </Box>
  );
};

export default StudentResultsPage;
