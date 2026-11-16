import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:iceibank_mobile/bank.dart';

void main() {
  test(
    'polling troca a líder, drena a fila e mostra o saldo real no 409',
    () async {
      var offline = false;
      var leader = 2;
      var saldo = 100;
      var revision = 1;
      final local = <String, String>{};
      Map<String, dynamic> snapshot() => {
        'id': 0,
        'saldo': saldo,
        'vetorAtual': [revision, 0, 0],
      };
      http.Response response(Object body, [int status = 200]) =>
          http.Response(jsonEncode(body), status);

      final bank = Bank(
        load: (key) async => local[key],
        save: (key, value) async => local[key] = value,
        client: MockClient((request) async {
          if (offline) throw http.ClientException('sem conexão');
          final agency = request.url.port - 4000;
          return switch (request.url.path) {
            '/auth/login' => response({
              'login': 'ana',
              'token': 'token-teste',
              'contas': [0],
            }),
            '/contas' => response(agency == 0 ? [snapshot()] : []),
            '/contas/0' => response(snapshot()),
            '/bully/lider' => response({
              'idAgencia': agency,
              'liderAtual': leader,
            }),
            '/contas/0/sincronizar' => (() {
              final op = jsonDecode(request.body);
              if (op['vetorConhecido'][0] != revision) {
                return response({
                  'erro': 'Conflito',
                  'contaAtual': snapshot(),
                }, 409);
              }
              saldo += ((op['valor'] as num) * (op['tipo'] == 'SAQUE' ? -1 : 1))
                  .round();
              revision++;
              return response(snapshot());
            })(),
            _ => response({'erro': 'Rota inesperada'}, 404),
          };
        }),
      );
      addTearDown(bank.dispose);
      await bank.signIn('ana', 'senha-teste');
      await bank.refreshAccount();
      await bank.poll();
      expect(bank.leader, 2);
      leader = 1;
      await bank.poll();
      expect(bank.leader, 1);

      offline = true;
      await bank.write('DEPOSITO', 1000);
      await bank.write('DEPOSITO', 500);
      await bank.write('SAQUE', 200);
      expect(bank.provisionalCents, 11300);
      expect(bank.pending, hasLength(3));
      expect(local['ana'], contains('operacaoId'));
      offline = false;
      await bank.poll();
      expect(bank.pending, isEmpty);
      expect(bank.provisionalCents, 11300);
      expect(saldo, 113);

      offline = true;
      await bank.write('DEPOSITO', 1000);
      saldo += 25;
      revision++;
      offline = false;
      await bank.poll();
      expect(bank.pending.single['erro'], 'Conflito');
      expect(bank.provisionalCents, 13800);
      expect(saldo, 138);
    },
  );
}
