import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../../api/client.dart';
import '../../api/operations_api.dart' as ops;
import '../../labels.dart';
import '../../state/auth.dart';
import '../../ui/widgets.dart';
import 'operations_screen.dart';

/// CC#32: en el expediente, el estado del cierre de la operación y el botón "Iniciar cierre".
class ExpedienteOperationCard extends StatefulWidget {
  const ExpedienteOperationCard({super.key, required this.expedienteId, required this.operationType});
  final String expedienteId;
  final String operationType;

  @override
  State<ExpedienteOperationCard> createState() => _ExpedienteOperationCardState();
}

class _ExpedienteOperationCardState extends State<ExpedienteOperationCard> {
  List<ops.OperationItem>? items;
  String side = 'SELLER';
  bool busy = false;

  @override
  void initState() {
    super.initState();
    ops.operationsOfExpediente(widget.expedienteId).then((r) {
      if (mounted) setState(() => items = r);
    }).catchError((_) {
      if (mounted) setState(() => items = []);
    });
  }

  @override
  Widget build(BuildContext context) {
    final auth = context.watch<AuthState>();
    if (!auth.can('OPERATION_VIEW_ALL') && !auth.can('OPERATION_MANAGE') && !auth.can('OPERATION_REVIEW')) return const SizedBox.shrink();
    final list = items;
    final active = list?.where((o) => o.status != 'CANCELLED').firstOrNull;
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: AppCard(
        child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
          const CardHeader(title: 'Cierre de la operación', description: 'Estado de la operación y su revisión documental (operación, PLD y PROFECO).'),
          if (list == null) const MutedText('Cargando…'),
          for (final o in list ?? <ops.OperationItem>[])
            ListTile(
              contentPadding: EdgeInsets.zero,
              title: Text(o.folio),
              subtitle: Text(o.closedOn != null ? 'Cerrada el ${formatDate(o.closedOn)} · ${o.closedByName ?? ''}' : '${o.requirementsSatisfied}/${o.requirementsTotal} requisitos cumplidos'),
              trailing: AppBadge(o.displayStatus, tone: operationTone(o)),
              onTap: () => context.push('/operaciones/${o.id}'),
            ),
          if (list != null && active == null && auth.can('OPERATION_MANAGE')) ...[
            AppSelect<String>(
              label: 'C21 Genera representa a',
              value: side,
              options: [for (final s in ['SELLER', 'BUYER', 'BOTH']) (value: s, label: ops.representedLabel(widget.operationType, s))],
              onChanged: (v) => setState(() => side = v),
            ),
            const SizedBox(height: 10),
            Btn('Iniciar cierre', icon: Icons.handshake_outlined, loading: busy, onPressed: busy
                ? null
                : () async {
                    setState(() => busy = true);
                    try {
                      final d = await ops.createOperation({'origin': 'OWN', 'expedienteId': widget.expedienteId, 'operationType': widget.operationType, 'representedSide': side});
                      if (context.mounted) context.push('/operaciones/${d.operation.id}');
                    } catch (e) {
                      if (context.mounted) showToast(context, errorText(e));
                    } finally {
                      if (mounted) setState(() => busy = false);
                    }
                  }),
          ],
        ]),
      ),
    );
  }
}
