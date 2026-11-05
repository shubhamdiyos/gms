import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../auth/presentation/providers/auth_provider.dart';
import '../../data/models/dashboard_models.dart';
import '../providers/dashboard_providers.dart';
import '../widgets/dashboard_widgets.dart';

/// Accountant dashboard backed by `GET /financial-dashboard`.
///
/// Only the aggregates that endpoint actually computes are shown. Expense,
/// profit, asset and liability cards are absent because the API returns them
/// as `null`; this screen does not turn that absence into a zero.
class AccountantDashboardScreen extends ConsumerWidget {
  const AccountantDashboardScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final user = ref.watch(currentUserProvider);
    final dashboard = ref.watch(accountantDashboardProvider);

    return DashboardScaffold(
      title: 'Accountant',
      onRefresh: () => _refresh(ref),
      children: [
        DashboardHeader(
          eyebrow: 'Finance workspace',
          title: user?.fullName.isNotEmpty == true
              ? user!.fullName
              : 'Accountant',
          subtitle: 'Fee collection and payment activity',
          icon: Icons.account_balance_wallet_outlined,
          color: AppColors.roleAccountant,
        ),
        const SizedBox(height: 20),
        AsyncDashboard<FinancialSummary>(
          value: dashboard,
          onRetry: () => _refresh(ref),
          builder: (context, data) => Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              MetricGrid(
                children: [
                  MetricCard(
                    label: 'Fees expected',
                    value: formatMoney(data.totalFeeExpected),
                    icon: Icons.request_quote_outlined,
                    color: AppColors.roleAccountant,
                  ),
                  MetricCard(
                    label: 'Fees collected',
                    value: formatMoney(data.totalFeeCollected),
                    icon: Icons.savings_outlined,
                    color: AppColors.success,
                  ),
                  MetricCard(
                    label: 'Fees outstanding',
                    value: formatMoney(data.totalFeeOutstanding),
                    icon: Icons.receipt_long_outlined,
                    color: AppColors.warning,
                  ),
                  MetricCard(
                    label: 'Collection rate',
                    value: formatPercent(data.feeCollectionRate),
                    icon: Icons.trending_up,
                    color: AppColors.secondary,
                  ),
                ],
              ),
              const SizedBox(height: 20),
              SectionCard(
                title: 'Collection position',
                child: Column(
                  children: [
                    InfoRow(
                      label: 'Expected this academic year',
                      value: formatMoney(data.totalFeeExpected),
                    ),
                    InfoRow(
                      label: 'Collected',
                      value: formatMoney(data.totalFeeCollected),
                    ),
                    InfoRow(
                      label: 'Outstanding',
                      value: formatMoney(data.totalFeeOutstanding),
                    ),
                    InfoRow(
                      label: 'Average fee per student',
                      value: formatMoney(data.averageFeePerStudent),
                    ),
                    const SizedBox(height: 10),
                    ClipRRect(
                      borderRadius: BorderRadius.circular(6),
                      child: LinearProgressIndicator(
                        value: (data.feeCollectionRate / 100).clamp(0, 1),
                        minHeight: 8,
                        color: AppColors.success,
                        backgroundColor: AppColors.gray100,
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 16),
              SectionCard(
                title: 'Payment activity',
                trailing: Text(
                  'Last 30 days',
                  style: Theme.of(context).textTheme.labelSmall?.copyWith(
                    color: AppColors.textTertiary,
                  ),
                ),
                child: Column(
                  children: [
                    InfoRow(
                      label: 'Successful payments',
                      value: data.totalPayments.toString(),
                    ),
                    InfoRow(
                      label: 'Average payment',
                      value: formatMoney(data.averagePaymentAmount),
                    ),
                    InfoRow(
                      label: 'Payments today',
                      value: data.paymentsToday.toString(),
                    ),
                    InfoRow(
                      label: 'Payments this week',
                      value: data.paymentsThisWeek.toString(),
                    ),
                    InfoRow(
                      label: 'Payments this month',
                      value: data.paymentsThisMonth.toString(),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 16),
              MetricGrid(
                children: [
                  MetricCard(
                    label: 'Students on roll',
                    value: data.totalStudents.toString(),
                    icon: Icons.groups_outlined,
                    color: AppColors.roleStudent,
                  ),
                  MetricCard(
                    label: 'Students with pending fees',
                    value: data.studentsWithPendingFees.toString(),
                    icon: Icons.pending_actions_outlined,
                    color: AppColors.warning,
                  ),
                  MetricCard(
                    label: 'Students overdue',
                    value: data.studentsWithOverdueFees.toString(),
                    icon: Icons.error_outline,
                    color: AppColors.error,
                  ),
                  MetricCard(
                    label: 'Overdue invoices',
                    value: data.overdueInvoices.toString(),
                    icon: Icons.description_outlined,
                    color: AppColors.error,
                  ),
                  MetricCard(
                    label: 'Upcoming due dates',
                    value: data.upcomingDueDates.toString(),
                    icon: Icons.event_outlined,
                    color: AppColors.primary,
                  ),
                ],
              ),
              if (data.lastUpdated != null) ...[
                const SizedBox(height: 12),
                Text(
                  'Aggregated ${formatDate(data.lastUpdated!.toLocal())}',
                  style: Theme.of(context).textTheme.bodySmall?.copyWith(
                    color: AppColors.textTertiary,
                  ),
                ),
              ],
            ],
          ),
        ),
      ],
    );
  }

  Future<void> _refresh(WidgetRef ref) async {
    ref.invalidate(accountantDashboardProvider);
    try {
      await ref.read(accountantDashboardProvider.future);
    } catch (_) {
      // The AsyncDashboard error state renders the failure.
    }
  }
}
