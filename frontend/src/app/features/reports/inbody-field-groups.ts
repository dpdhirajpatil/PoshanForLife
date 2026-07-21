import { InBodyData } from '../../core/models/report.model';

export interface InBodyFieldDef {
  key: keyof InBodyData;
  label: string;
  unit: string;
}

export interface InBodyFieldGroup {
  label: string;
  fields: InBodyFieldDef[];
}

/** Shared between the upload review screen and the report detail view. */
export const INBODY_FIELD_GROUPS: InBodyFieldGroup[] = [
  {
    label: 'Body composition',
    fields: [
      { key: 'weightKg', label: 'Weight', unit: 'kg' },
      { key: 'bodyFatPercent', label: 'Body fat', unit: '%' },
      { key: 'skeletalMuscleMassKg', label: 'Skeletal muscle mass', unit: 'kg' },
      { key: 'bodyFatMassKg', label: 'Body fat mass', unit: 'kg' },
      { key: 'fatFreeMassKg', label: 'Fat-free mass', unit: 'kg' },
      { key: 'bmi', label: 'BMI', unit: 'kg/m²' },
    ],
  },
  {
    label: 'Water & mineral',
    fields: [
      { key: 'bodyWaterL', label: 'Total body water', unit: 'L' },
      { key: 'intracellularWaterL', label: 'Intracellular water', unit: 'L' },
      { key: 'extracellularWaterL', label: 'Extracellular water', unit: 'L' },
      { key: 'proteinKg', label: 'Protein', unit: 'kg' },
      { key: 'mineralKg', label: 'Mineral', unit: 'kg' },
    ],
  },
  {
    label: 'Metabolic & targets',
    fields: [
      { key: 'basalMetabolicRate', label: 'Basal metabolic rate', unit: 'kcal' },
      { key: 'visceralFatLevel', label: 'Visceral fat level', unit: '' },
      { key: 'waistHipRatio', label: 'Waist-hip ratio', unit: '' },
      { key: 'obesityDegreePercent', label: 'Obesity degree', unit: '%' },
      { key: 'inbodyScore', label: 'InBody score', unit: 'pts' },
      { key: 'targetWeightKg', label: 'Target weight', unit: 'kg' },
      { key: 'weightControlKg', label: 'Weight control', unit: 'kg' },
      { key: 'fatControlKg', label: 'Fat control', unit: 'kg' },
      { key: 'muscleControlKg', label: 'Muscle control', unit: 'kg' },
    ],
  },
];
