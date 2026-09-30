class EarningsInput {
  final double kmPerDay;
  final double hoursPerDay;
  final int workDaysPerMonth;
  final double targetMonthlyEarnings;
  final double fuelEconomy;
  final double fuelPricePerUnit;
  final double monthlyMaintenance;
  final double monthlyInsurance;
  final double monthlyFinancing;
  final double monthlyRent;
  final double yearlyTax;
  final double monthlyOtherCosts;

  const EarningsInput({
    required this.kmPerDay,
    required this.hoursPerDay,
    required this.workDaysPerMonth,
    required this.targetMonthlyEarnings,
    required this.fuelEconomy,
    required this.fuelPricePerUnit,
    this.monthlyMaintenance = 0,
    this.monthlyInsurance = 0,
    this.monthlyFinancing = 0,
    this.monthlyRent = 0,
    this.yearlyTax = 0,
    this.monthlyOtherCosts = 0,
  });
}
