import Foundation

struct InBodyGroup: Identifiable {
    let id = UUID()
    let title: String
    let rows: [(label: String, value: String)]

    static let totalFieldCount = 20

    /// The backend models **no segmental (arm/trunk/leg) data**, so there is no
    /// Segmental Lean Analysis section to build — rendering an empty one would
    /// just look broken. "Goals & control" takes its place: Weight/Fat/Muscle
    /// Control are real InBody outputs, and they'd otherwise go unshown.
    ///
    /// Groups with no populated values are dropped entirely, since a partial
    /// scan can leave whole sections empty.
    static func groups(for data: InBodyData) -> [InBodyGroup] {
        let candidates: [(String, [(String, Double?, String)])] = [
            ("Body composition", [
                ("Total body water", data.bodyWaterL, "L"),
                ("Intracellular water", data.intracellularWaterL, "L"),
                ("Extracellular water", data.extracellularWaterL, "L"),
                ("Protein", data.proteinKg, "kg"),
                ("Minerals", data.mineralKg, "kg"),
                ("Body fat mass", data.bodyFatMassKg, "kg"),
            ]),
            ("Muscle-fat analysis", [
                ("Weight", data.weightKg, "kg"),
                ("Skeletal muscle mass", data.skeletalMuscleMassKg, "kg"),
                ("Body fat mass", data.bodyFatMassKg, "kg"),
                ("Fat-free mass", data.fatFreeMassKg, "kg"),
            ]),
            ("Obesity analysis", [
                ("BMI", data.bmi, ""),
                ("Body fat percentage", data.bodyFatPercent, "%"),
                ("Obesity degree", data.obesityDegreePercent, "%"),
                ("Waist-hip ratio", data.waistHipRatio, ""),
                ("Visceral fat level", data.visceralFatLevel, ""),
            ]),
            ("InBody score", [
                ("Score", data.inbodyScore.map(Double.init), "/ 100"),
                ("Basal metabolic rate", data.basalMetabolicRate, "kcal"),
            ]),
            ("Goals & control", [
                ("Target weight", data.targetWeightKg, "kg"),
                ("Weight control", data.weightControlKg, "kg"),
                ("Fat control", data.fatControlKg, "kg"),
                ("Muscle control", data.muscleControlKg, "kg"),
            ]),
        ]

        return candidates.compactMap { title, fields in
            let rows = fields.compactMap { label, value, unit -> (String, String)? in
                guard let value else { return nil }
                let formatted = String(format: "%.1f", locale: Locale(identifier: "en_US_POSIX"), value)
                return (label, unit.isEmpty ? formatted : "\(formatted) \(unit)")
            }
            return rows.isEmpty ? nil : InBodyGroup(title: title, rows: rows)
        }
    }
}
