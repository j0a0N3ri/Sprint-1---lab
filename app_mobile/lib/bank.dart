import 'dart:async';
import 'dart:convert';
import 'dart:math';

import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;

import 'store.dart';

typedef Json = Map<String, dynamic>;

class ApiException implements Exception {
  ApiException(this.status, this.body);
  final int status;
  final Json body;

  @override
  String toString() => '${body['erro'] ?? 'Erro HTTP $status'}';
}

class Bank extends ChangeNotifier {
  static const interval = Duration(seconds: 3);
  static const _configuredHost = String.fromEnvironment('BANK_HOST');
  static const _basePort = int.fromEnvironment('BANK_PORT', defaultValue: 4000);
  Bank({http.Client? client, this.load = readStore, this.save = writeStore})
    : _client = client ?? http.Client();

  final http.Client _client;
  final Future<String?> Function(String) load;
  final Future<void> Function(String, String) save;
  late final String _host = _configuredHost.isNotEmpty
      ? _configuredHost
      : !kIsWeb && defaultTargetPlatform == TargetPlatform.android
      ? '10.0.2.2'
      : '127.0.0.1';

  String? login;
  String? _token;
  String? notice;
  String? leadershipError;
  int? selected;
  int? leader;
  bool busy = false;
  bool _polling = false;
  bool _closed = false;
  List<int> accounts = [];
  final Map<int, Json> cache = {};
  final List<Json> pending = [];
  Set<int> reachable = {};
  Timer? _timer;

  Future<dynamic> _call(
    int agency,
    String path, {
    Json? body,
    bool auth = true,
  }) async {
    final request = http.Request(
      body == null ? 'GET' : 'POST',
      Uri.parse('http://$_host:${_basePort + agency}$path'),
    );
    if (auth && _token != null) {
      request.headers['Authorization'] = 'Bearer $_token';
    }
    if (body != null) {
      request.headers['Content-Type'] = 'application/json';
      request.body = jsonEncode(body);
    }
    final response = await (() async => http.Response.fromStream(
      await _client.send(request),
    ))().timeout(const Duration(seconds: 2));
    final decoded = jsonDecode(response.body);
    if (response.statusCode >= 400) {
      if (auth && response.statusCode == 401) _token = null;
      throw ApiException(response.statusCode, decoded);
    }
    return decoded;
  }

  bool get needsLogin => _token == null;

  Future<void> signIn(String user, String password) async {
    final normalized = user.trim();
    if (normalized.isEmpty || password.isEmpty) {
      throw const FormatException('Preencha o usuário e a senha.');
    }
    for (final id in [0, 1, 2]) {
      try {
        final result = await _call(
          id,
          '/auth/login',
          auth: false,
          body: {'login': normalized, 'senha': password},
        );
        final nextLogin = result['login'] as String;
        final stored = await load(Uri.encodeComponent(nextLogin));
        final saved = stored == null
            ? <String, dynamic>{}
            : jsonDecode(stored) as Json;
        cache
          ..clear()
          ..addEntries(
            ((saved['cache'] as Json?) ?? {}).entries.map(
              (entry) => MapEntry(int.parse(entry.key), entry.value as Json),
            ),
          );
        pending
          ..clear()
          ..addAll(((saved['pending'] as List?) ?? []).cast<Json>());
        accounts = (result['contas'] as List).cast<int>().toList()..sort();
        login = nextLogin;
        _token = result['token'] as String;
        final lists = await Future.wait([0, 1, 2].map(listAccounts));
        accounts = {
          ...accounts,
          ...cache.keys,
          ...lists.expand((ids) => ids),
        }.toList()..sort();
        selected = accounts.firstOrNull;
        _timer ??= Timer.periodic(interval, (_) => unawaited(poll()));
        return;
      } on ApiException {
        rethrow;
      } on Exception catch (error) {
        if (!_transport(error)) rethrow;
      }
    }
    throw const FormatException('Nenhuma agência respondeu ao login.');
  }

  Future<List<int>> listAccounts(int agency) async {
    try {
      final response = await _call(agency, '/contas') as List;
      return response.map((account) => account['id'] as int).toList();
    } on Exception catch (error) {
      if (!_transport(error)) rethrow;
      return [];
    }
  }

  void signOut() {
    _token = null;
    notifyListeners();
  }

  bool _transport(Exception error) =>
      error is TimeoutException || error is http.ClientException;

  Future<void> _save() async {
    await save(
      Uri.encodeComponent(login!),
      jsonEncode({
        'cache': cache.map((id, value) => MapEntry(id.toString(), value)),
        'pending': pending,
      }),
    );
  }

  Future<void> action(Future<void> Function() operation) async {
    if (busy || _closed) return;
    busy = true;
    notice = null;
    notifyListeners();
    try {
      await operation();
    } on Exception catch (error) {
      notice = _transport(error)
          ? 'Agência indisponível. Tente novamente.'
          : error.toString();
    } finally {
      busy = false;
      if (!_closed) notifyListeners();
    }
  }

  Future<void> select(int id) async {
    selected = id;
    await refreshAccount();
  }

  Future<void> refreshAccount() async {
    final id = selected;
    if (id == null || forAccount(id).isNotEmpty) return;
    try {
      cache[id] = await _call(id % 3, '/contas/$id');
      await _save();
    } on Exception catch (error) {
      if (!_transport(error) || !cache.containsKey(id)) rethrow;
    }
  }

  Future<void> createAccount(int id, String name) async {
    if (id < 0 || name.trim().isEmpty) {
      throw const FormatException('Informe um ID válido e um nome.');
    }
    await _call(
      id % 3,
      '/contas',
      body: {'id': id, 'nomeAluno': name.trim(), 'saldoInicial': 0},
    );
    if (!accounts.contains(id)) accounts.add(id);
    accounts.sort();
    await select(id);
  }

  List<Json> forAccount(int id) =>
      pending.where((op) => op['conta'] == id).toList();

  int get provisionalCents {
    final id = selected;
    if (id == null || cache[id] == null) return 0;
    final operations = forAccount(id);
    var cents = ((cache[id]!['saldo'] as num) * 100).round();
    if (operations.any((op) => op['erro'] != null)) return cents;
    for (final op in operations) {
      final amount = op['centavos'] as int;
      cents += op['tipo'] == 'DEPOSITO' ? amount : -amount;
    }
    return cents;
  }

  String _uuid() {
    final random = Random.secure();
    final bytes = List<int>.generate(16, (_) => random.nextInt(256));
    bytes[6] = (bytes[6] & 15) | 64;
    bytes[8] = (bytes[8] & 63) | 128;
    final hex = bytes.map((b) => b.toRadixString(16).padLeft(2, '0')).join();
    return '${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-'
        '${hex.substring(16, 20)}-${hex.substring(20)}';
  }

  Future<void> write(String type, int cents, {int? destination}) async {
    final id = selected;
    if (id == null || cents <= 0) {
      throw const FormatException('Informe um valor positivo.');
    }
    final queue = forAccount(id);
    if (queue.any((op) => op['erro'] != null)) {
      throw const FormatException(
        'Revise o conflito desta conta antes de continuar.',
      );
    }
    if (type == 'TRANSFERENCIA') {
      if (destination == null || destination < 0) {
        throw const FormatException('Informe uma conta de destino válida.');
      }
      if (queue.isNotEmpty) {
        throw const FormatException('Aguarde a sincronização da conta.');
      }
      final result = await _call(
        id % 3,
        '/transferencias',
        body: {'idOrigem': id, 'idDestino': destination, 'valor': cents / 100},
      );
      notice = result['mensagem'] as String?;
      await refreshAccount();
      return;
    }
    final snapshot = cache[id];
    if (snapshot == null) {
      throw const FormatException('Consulte o saldo antes de operar.');
    }
    if (type == 'SAQUE' && provisionalCents < cents) {
      throw const FormatException('Saldo disponível insuficiente.');
    }
    final operation = {
      'conta': id,
      'tipo': type,
      'centavos': cents,
      'vetorConhecido':
          queue.lastOrNull?['vetorConhecido'] ?? snapshot['vetorAtual'],
      'operacaoId': _uuid(),
    };
    pending.add(operation);
    try {
      await _save();
    } on Exception {
      pending.remove(operation);
      rethrow;
    }
    notifyListeners();
    await _sync();
  }

  Future<void> discard(int id) async {
    pending.removeWhere((op) => op['conta'] == id);
    await _save();
    notice = 'Fila da conta descartada. O saldo exibido é o último confirmado.';
  }

  Future<void> _sync() async {
    final blocked = <int>{};
    for (final op in List<Json>.of(pending)) {
      final id = op['conta'] as int;
      if (op['erro'] != null) blocked.add(id);
      if (blocked.contains(id)) continue;
      try {
        final result = await _call(
          id % 3,
          '/contas/$id/sincronizar',
          body: {
            'tipo': op['tipo'],
            'valor': (op['centavos'] as int) / 100,
            'vetorConhecido': op['vetorConhecido'],
            'operacaoId': op['operacaoId'],
          },
        );
        cache[id] = result;
        pending.remove(op);
        // Só encadeia operações que partiram da mesma revisão confirmada.
        for (final next in forAccount(id)) {
          if (!listEquals(
            next['vetorConhecido'] as List,
            op['vetorConhecido'] as List,
          ))
            break;
          next['vetorConhecido'] = result['vetorAtual'];
        }
        notice = 'Operação sincronizada.';
      } on ApiException catch (error) {
        if (error.status == 401 || error.status >= 500) rethrow;
        if (error.status == 409) cache[id] = error.body['contaAtual'] as Json;
        op['erro'] = error.toString();
        blocked.add(id);
      } on Exception catch (error) {
        if (!_transport(error)) rethrow;
        blocked.add(id);
      }
      await _save();
    }
  }

  Future<void> poll() async {
    if (_polling || needsLogin || _closed) return;
    _polling = true;
    try {
      final replies = await Future.wait(
        [0, 1, 2].map((id) async {
          try {
            return await _call(id, '/bully/lider', auth: false) as Json;
          } on Exception {
            return null;
          }
        }),
      );
      reachable = replies
          .whereType<Json>()
          .map((reply) => reply['idAgencia'] as int)
          .toSet();
      // Prefere a maior agência que se declara líder e respondeu nesta rodada.
      leader =
          replies.reversed
                  .whereType<Json>()
                  .where((reply) => reply['idAgencia'] == reply['liderAtual'])
                  .firstOrNull?['liderAtual']
              as int?;
      leadershipError = reachable.isEmpty ? 'Agências inacessíveis' : null;
      if (_closed) return;
      notifyListeners();
      if (!busy && !needsLogin) {
        busy = true;
        try {
          await _sync();
        } on Exception catch (error) {
          notice = error.toString();
        } finally {
          busy = false;
        }
      }
    } finally {
      _polling = false;
      if (!_closed) notifyListeners();
    }
  }

  @override
  void dispose() {
    _closed = true;
    _timer?.cancel();
    _client.close();
    super.dispose();
  }
}
