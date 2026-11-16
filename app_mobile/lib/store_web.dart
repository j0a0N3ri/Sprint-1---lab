import 'package:web/web.dart' as web;

Future<String?> readStore(String key) async =>
    web.window.localStorage.getItem('iceibank-$key');

Future<void> writeStore(String key, String value) async =>
    web.window.localStorage.setItem('iceibank-$key', value);
