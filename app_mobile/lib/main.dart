import 'dart:async';

import 'package:flutter/material.dart';

import 'bank.dart';

void main() => runApp(const IceiBankApp());

class IceiBankApp extends StatefulWidget {
  const IceiBankApp({super.key});

  @override
  State<IceiBankApp> createState() => _IceiBankAppState();
}

class _IceiBankAppState extends State<IceiBankApp> {
  final bank = Bank();
  final user = TextEditingController();
  final password = TextEditingController();

  @override
  void dispose() {
    bank.dispose();
    user.dispose();
    password.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => MaterialApp(
    title: 'ICEIBank',
    debugShowCheckedModeBanner: false,
    theme: ThemeData(
      useMaterial3: true,
      colorScheme: ColorScheme.fromSeed(seedColor: const Color(0xff006c58)),
      scaffoldBackgroundColor: const Color(0xfff3f7f5),
      inputDecorationTheme: const InputDecorationTheme(
        border: OutlineInputBorder(),
      ),
    ),
    home: ListenableBuilder(
      listenable: bank,
      builder: (context, _) => Scaffold(
        appBar: AppBar(
          backgroundColor: const Color(0xff003c32),
          foregroundColor: Colors.white,
          title: const Text(
            'ICEIBank',
            style: TextStyle(fontWeight: FontWeight.w700),
          ),
          actions: [
            if (!bank.needsLogin)
              IconButton(
                onPressed: bank.busy ? null : bank.signOut,
                icon: const Icon(Icons.logout),
                tooltip: 'Sair',
              ),
          ],
        ),
        body: SafeArea(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(20),
            child: Center(
              child: ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 550),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    if (bank.notice != null)
                      Padding(
                        padding: const EdgeInsets.only(bottom: 12),
                        child: Text(bank.notice!, key: const Key('notice')),
                      ),
                    if (bank.needsLogin) _login() else _dashboard(context),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    ),
  );

  Future<void> _submitLogin() async {
    await bank.action(() async {
      await bank.signIn(user.text, password.text);
      password.clear();
      await bank.refreshAccount();
    });
    unawaited(bank.poll());
  }

  Widget _login() => Column(
    crossAxisAlignment: CrossAxisAlignment.stretch,
    children: [
      const Text(
        'Entrar na sua conta',
        style: TextStyle(fontSize: 28, fontWeight: FontWeight.bold),
      ),
      const SizedBox(height: 20),
      TextField(
        controller: user,
        decoration: const InputDecoration(labelText: 'Usuário'),
        textInputAction: TextInputAction.next,
      ),
      const SizedBox(height: 12),
      TextField(
        controller: password,
        decoration: const InputDecoration(labelText: 'Senha'),
        obscureText: true,
        onSubmitted: (_) => _submitLogin(),
      ),
      const SizedBox(height: 16),
      FilledButton(
        onPressed: bank.busy ? null : _submitLogin,
        child: const Text('Entrar'),
      ),
    ],
  );

  Widget _dashboard(BuildContext context) {
    final id = bank.selected;
    final snapshot = bank.cache[id];
    final pending = id == null ? <Json>[] : bank.forAccount(id);
    final conflict = pending.any((op) => op['erro'] != null);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        _panel([
          const Text(
            'Liderança das agências',
            style: TextStyle(fontWeight: FontWeight.bold),
          ),
          const SizedBox(height: 5),
          Text(
            bank.leader == null
                ? bank.leadershipError ?? 'Aguardando eleição'
                : 'Agência ${bank.leader}',
            key: const Key('leader'),
            style: Theme.of(context).textTheme.headlineSmall,
          ),
          const SizedBox(height: 10),
          Wrap(
            spacing: 8,
            children: [
              for (final id in [0, 1, 2])
                Chip(
                  avatar: Icon(
                    bank.reachable.contains(id)
                        ? Icons.check_circle_outline
                        : Icons.portable_wifi_off,
                    size: 18,
                  ),
                  label: Text(
                    '$id · ${bank.reachable.contains(id) ? 'Ativa' : 'Sem resposta'}',
                  ),
                ),
            ],
          ),
        ], color: const Color(0xffd9f1e7)),
        const SizedBox(height: 14),
        _panel([
          Row(
            children: [
              Expanded(
                child: DropdownButtonFormField<int>(
                  key: ValueKey(id),
                  initialValue: id,
                  decoration: const InputDecoration(labelText: 'Conta'),
                  items: bank.accounts
                      .map(
                        (item) => DropdownMenuItem(
                          value: item,
                          child: Text('Conta $item'),
                        ),
                      )
                      .toList(),
                  onChanged: bank.busy
                      ? null
                      : (item) {
                          if (item != null) {
                            bank.action(() => bank.select(item));
                          }
                        },
                ),
              ),
              IconButton(
                tooltip: 'Atualizar saldo',
                onPressed: bank.busy || id == null
                    ? null
                    : () => bank.action(bank.refreshAccount),
                icon: const Icon(Icons.refresh),
              ),
              IconButton(
                tooltip: 'Criar conta',
                onPressed: bank.busy
                    ? null
                    : () => _openForm(context, 'Nova conta'),
                icon: const Icon(Icons.add_card),
              ),
            ],
          ),
          const SizedBox(height: 20),
          Text(
            snapshot == null
                ? 'Saldo indisponível'
                : conflict
                ? 'Saldo confirmado pela agência'
                : pending.isEmpty
                ? 'Saldo confirmado'
                : 'Saldo provisório',
            style: Theme.of(context).textTheme.titleMedium,
          ),
          if (snapshot != null)
            Text(
              _money(bank.provisionalCents),
              style: const TextStyle(fontSize: 36, fontWeight: FontWeight.w700),
            ),
        ]),
        if (pending.isNotEmpty) ...[
          const SizedBox(height: 12),
          _panel([
            Text(
              conflict
                  ? 'Sincronização bloqueada'
                  : 'Pendente de sincronização',
              style: const TextStyle(fontWeight: FontWeight.bold),
            ),
            for (final op in pending)
              Padding(
                padding: const EdgeInsets.only(top: 6),
                child: Text(
                  '${op['tipo'] == 'DEPOSITO' ? 'Depósito' : 'Saque'} '
                  '${_money(op['centavos'] as int)} · ${op['erro'] ?? 'Pendente'}',
                ),
              ),
            if (conflict)
              TextButton(
                onPressed: bank.busy
                    ? null
                    : () => bank.action(() => bank.discard(id!)),
                child: const Text('Descartar fila desta conta'),
              ),
          ], color: conflict ? const Color(0xffffe4df) : const Color(0xffffefcb)),
        ],
        const SizedBox(height: 14),
        Wrap(
          spacing: 10,
          runSpacing: 8,
          children: [
            for (final label in ['Depositar', 'Sacar', 'Transferir'])
              FilledButton.tonal(
                onPressed: bank.busy || snapshot == null || conflict
                    ? null
                    : () => _openForm(context, label),
                child: Text(label),
              ),
          ],
        ),
      ],
    );
  }

  Widget _panel(List<Widget> children, {Color color = Colors.white}) =>
      Container(
        padding: const EdgeInsets.all(18),
        decoration: BoxDecoration(
          color: color,
          borderRadius: BorderRadius.circular(16),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: children,
        ),
      );

  String _money(int cents) =>
      'R\$ ${(cents / 100).toStringAsFixed(2).replaceAll('.', ',')}';

  int? _cents(String input) {
    final raw = input.trim().replaceAll(',', '.');
    if (!RegExp(r'^\d+(\.\d{1,2})?$').hasMatch(raw)) return null;
    final parts = raw.split('.');
    final major = int.tryParse(parts[0]);
    if (major == null || major > 999999999) return null;
    return major * 100 +
        (parts.length == 1 ? 0 : int.parse(parts[1].padRight(2, '0')));
  }

  Future<void> _openForm(BuildContext context, String operation) async {
    final amount = TextEditingController();
    final other = TextEditingController();
    final creating = operation == 'Nova conta';
    final transfer = operation == 'Transferir';
    final result = await showDialog<List<String>>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(operation),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextField(
              controller: amount,
              autofocus: true,
              keyboardType: TextInputType.numberWithOptions(decimal: !creating),
              decoration: InputDecoration(
                labelText: creating ? 'ID da conta' : 'Valor em reais',
              ),
            ),
            if (creating || transfer) ...[
              const SizedBox(height: 12),
              TextField(
                controller: other,
                keyboardType: creating
                    ? TextInputType.name
                    : TextInputType.number,
                decoration: InputDecoration(
                  labelText: creating ? 'Nome da conta' : 'ID do destino',
                ),
              ),
            ],
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Cancelar'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, [amount.text, other.text]),
            child: const Text('Confirmar'),
          ),
        ],
      ),
    );
    // O diálogo ainda pode estar na animação de saída quando o resultado chega.
    unawaited(
      Future<void>.delayed(const Duration(milliseconds: 350), () {
        amount.dispose();
        other.dispose();
      }),
    );
    if (result == null || !mounted) return;
    await bank.action(() async {
      if (creating) {
        await bank.createAccount(int.tryParse(result[0]) ?? -1, result[1]);
        return;
      }
      final type = switch (operation) {
        'Depositar' => 'DEPOSITO',
        'Sacar' => 'SAQUE',
        _ => 'TRANSFERENCIA',
      };
      await bank.write(
        type,
        _cents(result[0]) ?? 0,
        destination: int.tryParse(result[1]),
      );
    });
  }
}
