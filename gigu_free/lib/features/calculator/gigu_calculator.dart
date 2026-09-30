import 'earnings_input.dart';
import 'earnings_result.dart';

class GiguCalculator {
  const GiguCalculator();

  EarningsResult calculate(EarningsInput input) {
    _validate(input);
    final monthlyKm = input.kmPerDay * input.workDaysPerMonth;
    final monthlyHours = input.hoursPerDay * input.workDaysPerMonth;
    final variableCostPerKm = input.fuelPricePerUnit / input.fuelEconomy;
    final fixedCostPerMonth = input.monthlyMaintenance +
        input.monthlyInsurance +
        input.monthlyFinancing +
        input.monthlyRent +
        (input.yearlyTax / 12.0) +
        input.monthlyOtherCosts;
    final fixedCostPerKm = monthlyKm > 0 ? fixedCostPerMonth / monthlyKm : 0;
    final costPerKm = variableCostPerKm + fixedCostPerKm;
    final totalMonthlyCost = costPerKm * monthlyKm;
    final requiredMonthlyRevenue = totalMonthlyCost + input.targetMonthlyEarnings;
    final targetRevenuePerKm = monthlyKm > 0 ? requiredMonthlyRevenue / monthlyKm : 0;
    final targetRevenuePerHour = monthlyHours > 0 ? requiredMonthlyRevenue / monthlyHours : 0;
    final dailyTargetRevenue = requiredMonthlyRevenue / input.workDaysPerMonth;
    final monthlyNetProfit = requiredMonthlyRevenue - totalMonthlyCost;
    final dailyNetProfit = monthlyNetProfit / input.workDaysPerMonth;
    final profitPct = requiredMonthlyRevenue > 0 ? (monthlyNetProfit / requiredMonthlyRevenue) * 100 : 0;

    return EarningsResult(
      monthlyKm: monthlyKm,
      monthlyHours: monthlyHours,
      variableCostPerKm: variableCostPerKm,
      fixedCostPerMonth: fixedCostPerMonth,
      fixedCostPerKm: fixedCostPerKm,
      costPerKm: costPerKm,
      totalMonthlyCost: totalMonthlyCost,
      targetRevenuePerKm: targetRevenuePerKm,
      targetRevenuePerHour: targetRevenuePerHour,
      dailyTargetRevenue: dailyTargetRevenue,
      dailyNetProfit: dailyNetProfit,
      monthlyNetProfit: monthlyNetProfit,
      profitPct: profitPct,
    );
  }

  void _validate(EarningsInput input) {
    if (input.kmPerDay <= 0) throw ArgumentError('Km por dia deve ser maior que zero.');
    if (input.hoursPerDay <= 0 || input.hoursPerDay > 24) throw ArgumentError('Horas por dia deve estar entre 0 e 24.');
    if (input.workDaysPerMonth <= 0 || input.workDaysPerMonth > 31) throw ArgumentError('Dias de trabalho deve estar entre 1 e 31.');
    if (input.fuelEconomy <= 0) throw ArgumentError('Consumo/autonomia deve ser maior que zero.');
  }
}
