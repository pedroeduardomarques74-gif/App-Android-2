class EarningsResult {
  final double monthlyKm;
  final double monthlyHours;
  final double variableCostPerKm;
  final double fixedCostPerMonth;
  final double fixedCostPerKm;
  final double costPerKm;
  final double totalMonthlyCost;
  final double targetRevenuePerKm;
  final double targetRevenuePerHour;
  final double dailyTargetRevenue;
  final double dailyNetProfit;
  final double monthlyNetProfit;
  final double profitPct;

  const EarningsResult({
    required this.monthlyKm,
    required this.monthlyHours,
    required this.variableCostPerKm,
    required this.fixedCostPerMonth,
    required this.fixedCostPerKm,
    required this.costPerKm,
    required this.totalMonthlyCost,
    required this.targetRevenuePerKm,
    required this.targetRevenuePerHour,
    required this.dailyTargetRevenue,
    required this.dailyNetProfit,
    required this.monthlyNetProfit,
    required this.profitPct,
  });
}
