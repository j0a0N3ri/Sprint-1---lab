import 'dart:io';

import 'package:path_provider/path_provider.dart';

Future<File> _file(String key) async {
  final folder = await getApplicationSupportDirectory();
  await folder.create(recursive: true);
  return File('${folder.path}/iceibank-$key.json');
}

Future<String?> readStore(String key) async {
  final file = await _file(key);
  return await file.exists() ? file.readAsString() : null;
}

Future<void> writeStore(String key, String value) async {
  final file = await _file(key);
  final temporary = File('${file.path}.tmp');
  await temporary.writeAsString(value, flush: true);
  await temporary.rename(file.path);
}
