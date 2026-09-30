import 'package:flutter/material.dart';
import '../calculator/calculator_screen.dart';

class HomeScreen extends StatelessWidget {
  const HomeScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(20),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Row(children: [
                const CircleAvatar(radius: 26, child: Icon(Icons.directions_car_filled_rounded, size: 30)),
                const SizedBox(width: 12),
                const Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Text('GigU', style: TextStyle(fontSize: 28, fontWeight: FontWeight.w800)),
                  Text('Reconstruido • acesso livre', style: TextStyle(color: Colors.white70)),
                ])),
              ]),
              const SizedBox(height: 30),
              const Text('Ferramentas', style: TextStyle(fontSize: 20, fontWeight: FontWeight.w700)),
              const SizedBox(height: 12),
              _Tile(
                icon: Icons.calculate_rounded,
                title: 'Lucro e custo por km',
                subtitle: 'Calcule meta, custos, ganho por km e por hora.',
                onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const CalculatorScreen())),
              ),
              const SizedBox(height: 12),
              const _Tile(icon: Icons.history, title: 'Historico', subtitle: 'Estrutura preparada para registrar calculos.'),
              const SizedBox(height: 12),
              const _Tile(icon: Icons.speed, title: 'Radar / desempenho', subtitle: 'Modulo a reconstruir a partir do APK.'),
            ],
          ),
        ),
      ),
    );
  }
}

class _Tile extends StatelessWidget {
  final IconData icon;
  final String title;
  final String subtitle;
  final VoidCallback? onTap;
  const _Tile({required this.icon, required this.title, required this.subtitle, this.onTap});

  @override
  Widget build(BuildContext context) {
    return Card(
      child: InkWell(
        borderRadius: BorderRadius.circular(18),
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.all(18),
          child: Row(children: [
            CircleAvatar(radius: 24, child: Icon(icon)),
            const SizedBox(width: 14),
            Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text(title, style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w700)),
              const SizedBox(height: 4),
              Text(subtitle, style: const TextStyle(color: Colors.white70)),
            ])),
            const Icon(Icons.chevron_right),
          ]),
        ),
      ),
    );
  }
}
