import 'package:flutter/material.dart';
import 'features/home/home_screen.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const GigUApp());
}

class GigUApp extends StatelessWidget {
  const GigUApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      title: 'GigU FREE',
      theme: ThemeData(
        useMaterial3: true,
        brightness: Brightness.dark,
        colorSchemeSeed: const Color(0xFF5A67F2),
        scaffoldBackgroundColor: const Color(0xFF0F1117),
      ),
      home: const HomeScreen(),
    );
  }
}
