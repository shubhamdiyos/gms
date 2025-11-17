import { Box, Typography, Paper, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, Chip } from '@mui/material';
import { AttachMoney } from '@mui/icons-material';
import { useGetMyFeesQuery } from '../../store/api/apiSlice';
import dayjs from 'dayjs';

const StudentFeesPage = () => {
  const { data: fees = [], isLoading } = useGetMyFeesQuery();

  const getStatusColor = (status) => {
    if (status === 'PAID') return 'success';
    if (status === 'PARTIAL') return 'warning';
    return 'error';
  };

  return (
    <Box>
      {/* Header */}
      <Box sx={{ mb: 4 }}>
        <Typography variant="h4" component="h1" gutterBottom>
          My Fees
        </Typography>
        <Typography variant="body1" color="text.secondary">
          View your fee status and payment history.
        </Typography>
      </Box>

      <Paper>
        <Box sx={{ p: 2, borderBottom: '1px solid #e0e0e0', display: 'flex', alignItems: 'center' }}>
          <AttachMoney sx={{ mr: 1, color: 'primary.main' }} />
          <Typography variant="h6">Fee Details</Typography>
        </Box>

        {isLoading ? (
          <Box sx={{ p: 4, textAlign: 'center' }}>
            <Typography>Loading fees...</Typography>
          </Box>
        ) : fees.length === 0 ? (
          <Box sx={{ p: 4, textAlign: 'center' }}>
            <Typography variant="body1" color="text.secondary">
              No fee records available.
            </Typography>
          </Box>
        ) : (
          <TableContainer>
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell>Fee</TableCell>
                  <TableCell>Status</TableCell>
                  <TableCell>Amount</TableCell>
                  <TableCell>Paid Amount</TableCell>
                  <TableCell>Due Date</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {fees.map((fee) => (
                  <TableRow key={fee.id}>
                    <TableCell>{fee.feeName || 'Fee'}</TableCell>
                    <TableCell>
                      <Chip
                        label={fee.status}
                        color={getStatusColor(fee.status)}
                        size="small"
                      />
                    </TableCell>
                    <TableCell>{fee.amount != null ? `₹${fee.amount}` : '-'}</TableCell>
                    <TableCell>{fee.amountPaid != null ? `₹${fee.amountPaid}` : '-'}</TableCell>
                    <TableCell>
                      {fee.dueDate ? dayjs(fee.dueDate).format('MMM DD, YYYY') : '-'}
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

export default StudentFeesPage;
