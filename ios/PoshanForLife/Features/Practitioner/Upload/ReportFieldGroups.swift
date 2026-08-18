import Foundation

struct FieldSpec: Identifiable {
    let key: String
    let label: String
    let unit: String
    var isInt: Bool = false
    var id: String { key }
}

struct FieldGroupSpec: Identifiable {
    let title: String
    let fields: [FieldSpec]
    var id: String { title }
}

/// Backend's `InBodyData` is a flat 20-field scaffold with no server-side
/// grouping — these groups follow the section names from the original
/// Expo/web spec ("Body Composition, Muscle-Fat, Obesity, Score, Segmental,
/// Impedance"), best-fit-mapped onto the 20 fields that actually exist.
/// "Segmental" (per-limb lean mass) is omitted entirely — no backend field
/// models it. "Impedance" holds intracellular/extracellular water as the
/// closest available proxy. Mirrors Android's `REPORT_FIELD_GROUPS` exactly,
/// field-for-field, so the two clients group the same 20 values the same way.
enum ReportFieldGroups {
    static let all: [FieldGroupSpec] = [
        FieldGroupSpec(title: "Body Composition", fields: [
            FieldSpec(key: "weightKg", label: "Weight", unit: "kg"),
            FieldSpec(key: "bodyWaterL", label: "Total body water", unit: "L"),
            FieldSpec(key: "proteinKg", label: "Protein", unit: "kg"),
            FieldSpec(key: "mineralKg", label: "Mineral", unit: "kg"),
            FieldSpec(key: "bodyFatMassKg", label: "Body fat mass", unit: "kg"),
            FieldSpec(key: "fatFreeMassKg", label: "Fat-free mass", unit: "kg"),
        ]),
        FieldGroupSpec(title: "Muscle-Fat", fields: [
            FieldSpec(key: "skeletalMuscleMassKg", label: "Skeletal muscle mass", unit: "kg"),
            FieldSpec(key: "bodyFatPercent", label: "Body fat %", unit: "%"),
        ]),
        FieldGroupSpec(title: "Obesity", fields: [
            FieldSpec(key: "bmi", label: "BMI", unit: ""),
            FieldSpec(key: "waistHipRatio", label: "Waist-hip ratio", unit: ""),
            FieldSpec(key: "visceralFatLevel", label: "Visceral fat level", unit: ""),
            FieldSpec(key: "obesityDegreePercent", label: "Obesity degree", unit: "%"),
            FieldSpec(key: "targetWeightKg", label: "Target weight", unit: "kg"),
            FieldSpec(key: "weightControlKg", label: "Weight control", unit: "kg"),
            FieldSpec(key: "fatControlKg", label: "Fat control", unit: "kg"),
            FieldSpec(key: "muscleControlKg", label: "Muscle control", unit: "kg"),
        ]),
        FieldGroupSpec(title: "Score", fields: [
            FieldSpec(key: "inbodyScore", label: "InBody score", unit: "", isInt: true),
            FieldSpec(key: "basalMetabolicRate", label: "Basal metabolic rate", unit: "kcal"),
        ]),
        FieldGroupSpec(title: "Impedance", fields: [
            FieldSpec(key: "intracellularWaterL", label: "Intracellular water", unit: "L"),
            FieldSpec(key: "extracellularWaterL", label: "Extracellular water", unit: "L"),
        ]),
    ]
}

extension InBodyData {
    func value(for key: String) -> Double? {
        switch key {
        case "weightKg": return weightKg
        case "bodyFatPercent": return bodyFatPercent
        case "skeletalMuscleMassKg": return skeletalMuscleMassKg
        case "bmi": return bmi
        case "visceralFatLevel": return visceralFatLevel
        case "bodyWaterL": return bodyWaterL
        case "proteinKg": return proteinKg
        case "mineralKg": return mineralKg
        case "basalMetabolicRate": return basalMetabolicRate
        case "bodyFatMassKg": return bodyFatMassKg
        case "fatFreeMassKg": return fatFreeMassKg
        case "waistHipRatio": return waistHipRatio
        case "targetWeightKg": return targetWeightKg
        case "weightControlKg": return weightControlKg
        case "fatControlKg": return fatControlKg
        case "muscleControlKg": return muscleControlKg
        case "obesityDegreePercent": return obesityDegreePercent
        case "intracellularWaterL": return intracellularWaterL
        case "extracellularWaterL": return extracellularWaterL
        case "inbodyScore": return inbodyScore.map(Double.init)
        default: return nil
        }
    }

    mutating func setValue(_ value: Double?, for key: String) {
        switch key {
        case "weightKg": weightKg = value
        case "bodyFatPercent": bodyFatPercent = value
        case "skeletalMuscleMassKg": skeletalMuscleMassKg = value
        case "bmi": bmi = value
        case "visceralFatLevel": visceralFatLevel = value
        case "bodyWaterL": bodyWaterL = value
        case "proteinKg": proteinKg = value
        case "mineralKg": mineralKg = value
        case "basalMetabolicRate": basalMetabolicRate = value
        case "bodyFatMassKg": bodyFatMassKg = value
        case "fatFreeMassKg": fatFreeMassKg = value
        case "waistHipRatio": waistHipRatio = value
        case "targetWeightKg": targetWeightKg = value
        case "weightControlKg": weightControlKg = value
        case "fatControlKg": fatControlKg = value
        case "muscleControlKg": muscleControlKg = value
        case "obesityDegreePercent": obesityDegreePercent = value
        case "intracellularWaterL": intracellularWaterL = value
        case "extracellularWaterL": extracellularWaterL = value
        case "inbodyScore": inbodyScore = value.map { Int($0) }
        default: break
        }
    }
}
