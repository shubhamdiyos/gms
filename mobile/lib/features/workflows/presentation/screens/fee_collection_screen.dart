import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/api/api_exceptions.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../dashboard/data/models/dashboard_models.dart';
import '../../../dashboard/presentation/widgets/dashboard_widgets.dart';
import '../../data/models/workflow_models.dart';
import '../providers/workflow_providers.dart';
import '../widgets/workflow_widgets.dart';

/// The accountant's collection ledger: outstanding fees and the payment form.
class FeeCollectionScreen extends ConsumerStatefulWidget {
  const FeeCollectionScreen({super.key});

  @override
  ConsumerState<FeeCollectionScreen> createState() =>
      _FeeCollectionScreenState();
}

class _FeeCollectionScreenState extends ConsumerState<FeeCollectionScreen> {
  final TextEditingController _search = TextEditingController();
  bool _outstandingOnly = true;
  String _filter = '';

  @override
  void dispose() {
    _search.dispose();
    super.dispose();
  }

  Future<void> _reload() async {
    ref.invalidate(feeLedgerProvider);
    try {
      await ref.read(feeLedgerProvider.future);
    } catch (_) {
      // Rendered by the error state.
    }
  }

  Future<void> _collect(FeeRecord fee) async {
    final receipt = await showDialog<PaymentReceipt>(
      context: context,
      builder: (dialogContext) => _CollectFeeDialog(fee: fee),
    );
    if (receipt == null || !mounted) return;

    await _reload();
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(
          'Payment ${receipt.paymentReference} recorded for '
          '${receipt.studentName ?? 'the student'}.',
        ),
      ),
    );
  }

  List<FeeRecord> _visible(List<FeeRecord> fees) {
    final needle = _filter.trim().toLowerCase();
    return fees.where((fee) {
      if (_outstandingOnly && !fee.isOutstanding) return false;
      if (needle.isEmpty) return true;
      final text =
          '${fee.studentName ?? ''} ${fee.feeType ?? ''} ${fee.description ?? ''}'
              .toLowerCase();
      return text.contains(needle);
    }).toList();
  }

  @override
  Widget build(BuildContext context) {
    final ledger = ref.watch(feeLedgerProvider);

    return WorkflowScaffold(
      title: 'Fee collection',
      subtitle: 'Record a payment against an outstanding fee',
      children: [
        TextField(
          controller: _search,
          onChanged: (value) => setState(() => _filter = value),
          decoration: InputDecoration(
            hintText: 'Filter by student or fee type',
            prefixIcon: const Icon(Icons.filter_list),
            border: OutlineInputBorder(
              borderRadius: BorderRadius.circular(12),
            ),
            isDense: true,
          ),
        ),
        const SizedBox(height: 12),
        FilterChipRow(
          showOnlyOutstanding: _outstandingOnly,
          onChanged: (value) => setState(() => _outstandingOnly = value),
        ),
        const SizedBox(height: 16),
        AsyncContent<List<FeeRecord>>(
          value: ledger,
          onRetry: _reload,
          errorTitle: 'Could not load the fee ledger',
          builder: (context, fees) {
            final visible = _visible(fees);
            final outstanding = fees.totalOutstanding;
            return Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                MetricGrid(
                  children: [
                    MetricCard(
                      label: 'Outstanding',
                      value: formatMoney(outstanding),
                      icon: Icons.account_balance_wallet_outlined,
                      color: AppColors.roleAccountant,
                    ),
                    MetricCard(
                      label: 'Fees in view',
                      value: visible.length.toString(),
                      icon: Icons.receipt_long_outlined,
                      color: AppColors.primary,
                    ),
                    MetricCard(
                      label: 'Collected',
                      value: formatMoney(fees.totalPaid),
                      icon: Icons.savings_outlined,
                      color: AppColors.success,
                    ),
                  ],
                ),
                const SizedBox(height: 16),
                SectionCard(
                  title: 'Ledger',
                  trailing: Text(
                    '${fees.length} fee records',
                    style: Theme.of(context).textTheme.labelSmall?.copyWith(
                      color: AppColors.textTertiary,
                    ),
                  ),
                  child: visible.isEmpty
                      ? const EmptySection(
                          message:
                              'No fees match this filter. Try "All fees" or '
                              'clear the search.',
                          icon: Icons.receipt_long_outlined,
                        )
                      : Column(
                          children: [
                            for (final fee in visible)
                              _FeeRow(
                                fee: fee,
                                onCollect: fee.isOutstanding
                                    ? () => _collect(fee)
                                    : null,
                              ),
                          ],
                        ),
                ),
              ],
            );
          },
        ),
      ],
    );
  }
}

class _FeeRow extends StatelessWidget {
  final FeeRecord fee;
  final VoidCallback? onCollect;

  const _FeeRow({required this.fee, this.onCollect});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: Row(
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  fee.studentName ?? 'Student',
                  style: Theme.of(context).textTheme.bodyLarge,
                ),
                Text(
                  '${fee.label} · ${formatMoney(fee.balanceAmount)} balance',
                  style: Theme.of(context).textTheme.bodySmall?.copyWith(
                    color: AppColors.textSecondary,
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(width: 8),
          StatusPill(status: fee.status),
          const SizedBox(width: 8),
          if (onCollect != null)
            FilledButton.tonal(
              onPressed: onCollect,
              child: const Text('Collect'),
            ),
        ],
      ),
    );
  }
}

class _CollectFeeDialog extends ConsumerStatefulWidget {
  final FeeRecord fee;

  const _CollectFeeDialog({required this.fee});

  @override
  ConsumerState<_CollectFeeDialog> createState() => _CollectFeeDialogState();
}

class _CollectFeeDialogState extends ConsumerState<_CollectFeeDialog> {
  late final TextEditingController _amount = TextEditingController(
    text: widget.fee.balanceAmount.toStringAsFixed(2),
  );
  final TextEditingController _reference = TextEditingController();
  final TextEditingController _remarks = TextEditingController();
  String _method = FeePaymentInput.methods.first;
  DateTime _date = startOfDay(DateTime.now());
  bool _saving = false;
  String? _error;

  @override
  void dispose() {
    _amount.dispose();
    _reference.dispose();
    _remarks.dispose();
    super.dispose();
  }

  Future<void> _pickDate() async {
    final picked = await showDatePicker(
      context: context,
      initialDate: _date,
      firstDate: DateTime(2020),
      lastDate: startOfDay(DateTime.now()),
    );
    if (picked != null) {
      setState(() => _date = startOfDay(picked));
    }
  }

  Future<void> _submit() async {
    final amount = double.tryParse(_amount.text.trim());
    if (amount == null || amount <= 0) {
      setState(() => _error = 'Enter an amount greater than zero.');
      return;
    }
    if (amount > widget.fee.balanceAmount + 0.001) {
      setState(() {
        _error =
            'The amount exceeds the ${formatMoney(widget.fee.balanceAmount)} '
            'balance.';
      });
      return;
    }
    if (_reference.text.trim().isEmpty) {
      setState(() => _error = 'A payment reference is required.');
      return;
    }

    setState(() {
      _saving = true;
      _error = null;
    });

    try {
      final receipt = await ref
          .read(workflowRepositoryProvider)
          .recordPayment(
            FeePaymentInput(
              studentFeeId: widget.fee.id,
              amount: amount,
              paymentDate: _date,
              paymentMethod: _method,
              paymentReference: _reference.text.trim(),
              remarks: _remarks.text.trim().isEmpty
                  ? null
                  : _remarks.text.trim(),
            ),
          );
      if (!mounted) return;
      Navigator.of(context).pop(receipt);
    } catch (error) {
      if (!mounted) return;
      setState(() {
        _saving = false;
        _error = error is ApiException
            ? error.message
            : 'The payment could not be recorded.';
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: Text('Collect ${widget.fee.label}'),
      content: SizedBox(
        width: 420,
        child: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text(
                '${widget.fee.studentName ?? 'Student'} · balance '
                '${formatMoney(widget.fee.balanceAmount)}',
                style: Theme.of(context).textTheme.bodySmall?.copyWith(
                  color: AppColors.textSecondary,
                ),
              ),
              const SizedBox(height: 16),
              TextField(
                controller: _amount,
                enabled: !_saving,
                keyboardType: const TextInputType.numberWithOptions(
                  decimal: true,
                ),
                inputFormatters: [
                  FilteringTextInputFormatter.allow(RegExp(r'^\d*\.?\d{0,2}')),
                ],
                decoration: const InputDecoration(
                  labelText: 'Amount',
                  prefixText: '₹',
                  border: OutlineInputBorder(),
                ),
              ),
              const SizedBox(height: 12),
              DropdownButtonFormField<String>(
                initialValue: _method,
                decoration: const InputDecoration(
                  labelText: 'Payment method',
                  border: OutlineInputBorder(),
                ),
                items: [
                  for (final method in FeePaymentInput.methods)
                    DropdownMenuItem(
                      value: method,
                      child: Text(method.replaceAll('_', ' ')),
                    ),
                ],
                onChanged: _saving
                    ? null
                    : (value) =>
                          setState(() => _method = value ?? _method),
              ),
              const SizedBox(height: 12),
              TextField(
                controller: _reference,
                enabled: !_saving,
                decoration: const InputDecoration(
                  labelText: 'Payment reference',
                  hintText: 'Receipt or transaction number',
                  border: OutlineInputBorder(),
                ),
              ),
              const SizedBox(height: 12),
              OutlinedButton.icon(
                onPressed: _saving ? null : _pickDate,
                icon: const Icon(Icons.event_outlined),
                label: Text('Payment date ${formatDate(_date)}'),
              ),
              const SizedBox(height: 12),
              TextField(
                controller: _remarks,
                enabled: !_saving,
                maxLines: 2,
                decoration: const InputDecoration(
                  labelText: 'Remarks (optional)',
                  border: OutlineInputBorder(),
                ),
              ),
              if (_error != null) ...[
                const SizedBox(height: 12),
                Text(
                  _error!,
                  style: Theme.of(context).textTheme.bodySmall?.copyWith(
                    color: AppColors.error,
                  ),
                ),
              ],
            ],
          ),
        ),
      ),
      actions: [
        TextButton(
          onPressed: _saving ? null : () => Navigator.of(context).pop(),
          child: const Text('Cancel'),
        ),
        FilledButton(
          onPressed: _saving ? null : _submit,
          child: _saving
              ? const SizedBox(
                  width: 16,
                  height: 16,
                  child: CircularProgressIndicator(strokeWidth: 2),
                )
              : const Text('Record payment'),
        ),
      ],
    );
  }
}
