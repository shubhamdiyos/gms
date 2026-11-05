import { Box, Typography, Paper, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, Chip } from '@mui/material';
import { AttachMoney } from '@mui/icons-material';
import dayjs from 'dayjs';
import { useGetMyFeesQuery } from '../../store/api/apiSlice';

const money = (value) =>
  value == null ? '-' : `₹${new Intl.NumberFormat('en-IN', { maximumFractionDigits: 2 }).format(value)}`;

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
                  <TableCell>Academic Year</TableCell>
                  <TableCell align="right">Total</TableCell>
                  <TableCell align="right">Paid</TableCell>
                  <TableCell align="right">Balance</TableCell>
                  <TableCell>Due Date</TableCell>
                  <TableCell>Status</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {fees.map((fee) => (
                  <TableRow key={fee.id}>
                    <TableCell>
                      <Typography variant="body2">{fee.feeType || `Fee ${fee.feeId ?? fee.id}`}</Typography>
                      {fee.description && (
                        <Typography variant="caption" color="text.secondary">
                          {fee.description}
                        </Typography>
                      )}
                    </TableCell>
                    <TableCell>{fee.academicYear || '-'}</TableCell>
                    <TableCell align="right">{money(fee.totalAmount)}</TableCell>
                    <TableCell align="right">{money(fee.amountPaid)}</TableCell>
                    <TableCell align="right">{money(fee.balanceAmount)}</TableCell>
                    <TableCell>{fee.dueDate ? dayjs(fee.dueDate).format('MMM DD, YYYY') : '-'}</TableCell>
                    <TableCell>
                      <Chip
                        label={fee.status}
                        color={getStatusColor(fee.status)}
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

export default StudentFeesPage;
