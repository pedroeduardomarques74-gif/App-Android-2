import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'earnings_input.dart';
import 'gigu_calculator.dart';

class CalculatorScreen extends StatefulWidget {
  const CalculatorScreen({super.key});
  @override
  State<CalculatorScreen> createState() => _CalculatorScreenState();
}

class _CalculatorScreenState extends State<CalculatorScreen> {
  final _km = TextEditingController(text: '200');
  final _hours = TextEditingController(text: '8');
  final _days = TextEditingController(text: '26');
  final _target = TextEditingController(text: '5000');
  final _economy = TextEditingController(text: '10');
  final _fuel = TextEditingController(text: '6.00');
  final _maintenance = TextEditingController(text: '300');
  final _insurance = TextEditingController(text: '250');
  final _financing = TextEditingController(text: '0');
  final _rent = TextEditingController(text: '0');
  final _tax = TextEditingController(text: '2400');
  final _other = TextEditingController(text: '200');

  final _calculator = const GiguCalculator();
  dynamic _result;
  String? _error;

  double _d(TextEditingController c) => double.tryParse(c.text.replaceAll(',', '.')) ?? 0;
  int _i(TextEditingController c) => int.tryParse(c.text) ?? 0;

  void _calculate() {
    try {
      final r = _calculator.calculate(EarningsInput(
        kmPerDay: _d(_km),
        hoursPerDay: _d(_hours),
        workDaysPerMonth: _i(_days),
        targetMonthlyEarnings: _d(_target),
        fuelEconomy: _d(_economy),
        fuelPricePerUnit: _d(_fuel),
        monthlyMaintenance: _d(_maintenance),
        monthlyInsurance: _d(_insurance),
        monthlyFinancing: _d(_financing),
        monthlyRent: _d(_rent),
        yearlyTax: _d(_tax),
        monthlyOtherCosts: _d(_other),
      ));
      setState(() { _result = r; _error = null; });
    } catch (e) {
      setState(() { _error = e.toString(); _result = null; });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Calcular lucro e ganhos ideais')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          const Text('Informe sua meta e custos para calcular seu ganho por hora e por km ideal.', style: TextStyle(color: Colors.white70)),
          const SizedBox(height: 18),
          _field('Km rodados por dia', _km),
          _field('Horas dirigidas por dia', _hours),
          _field('Dias trabalhados no mes', _days),
          _field('Meta de lucro mensal (R\$)', _target),
          const Divider(height: 32),
          _field('Consumo (km/L ou km/kWh)', _economy),
          _field('Preco combustivel/energia (R\$)', _fuel),
          _field('Manutencao mensal (R\$)', _maintenance),
          _field('Seguro mensal (R\$)', _insurance),
          _field('Financiamento mensal (R\$)', _financing),
          _field('Aluguel mensal (R\$)', _rent),
          _field('Imposto anual (R\$)', _tax),
          _field('Outros custos mensais (R\$)', _other),
          const SizedBox(height: 10),
          FilledButton.icon(
            onPressed: _calculate,
            icon: const Icon(Icons.calculate),
            label: const Padding(padding: EdgeInsets.symmetric(vertical: 14), child: Text('CALCULAR')),
          ),
          if (_error != null) Padding(padding: const EdgeInsets.only(top: 12), child: Text(_error!, style: const TextStyle(color: Colors.redAccent))),
          if (_result != null) ...[
            const SizedBox(height: 20),
            _results(_result),
          ],
        ],
      ),
    );
  }

  Widget _field(String label, TextEditingController controller) => Padding(
    padding: const EdgeInsets.only(bottom: 10),
    child: TextField(
      controller: controller,
      keyboardType: const TextInputType.numberWithOptions(decimal: true),
      decoration: InputDecoration(labelText: label, border: const OutlineInputBorder()),
    ),
  );

  Widget _results(dynamic r) {
    final money = NumberFormat.currency(locale: 'pt_BR', symbol: 'R\$');
    return Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
      const Text('Resultado', style: TextStyle(fontSize: 22, fontWeight: FontWeight.bold)),
      const SizedBox(height: 12),
      _metric('Custo por km', money.format(r.costPerKm)),
      _metric('Ganho ideal por km', money.format(r.targetRevenuePerKm)),
      _metric('Ganho por hora', money.format(r.targetRevenuePerHour)),
      _metric('Meta diaria de faturamento', money.format(r.dailyTargetRevenue)),
      _metric('Lucro diario', money.format(r.dailyNetProfit)),
      _metric('Lucro liquido mensal', money.format(r.monthlyNetProfit)),
      _metric('Margem de lucro', '${r.profitPct.toStringAsFixed(1)}%'),
      _metric('Custo mensal total', money.format(r.totalMonthlyCost)),
    ]);
  }

  Widget _metric(String title, String value) => Card(
    margin: const EdgeInsets.only(bottom: 8),
    child: Padding(
      padding: const EdgeInsets.all(16),
      child: Row(children: [
        Expanded(child: Text(title)),
        Text(value, style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 17)),
      ]),
    ),
  );
}
