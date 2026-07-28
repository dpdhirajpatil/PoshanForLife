export type BadgeCriteriaType =
  | 'challenge_completed'
  | 'programme_count'
  | 'streak_days'
  | 'custom';

export const BADGE_CRITERIA_TYPES: BadgeCriteriaType[] = [
  'challenge_completed',
  'programme_count',
  'streak_days',
  'custom',
];

export const BADGE_CRITERIA_TYPE_LABELS: Record<BadgeCriteriaType, string> = {
  challenge_completed: 'Challenge completed',
  programme_count: 'Programme count',
  streak_days: 'Streak days',
  custom: 'Custom',
};

/** The admin-managed badge catalog — a small, mostly-static reference table. */
export interface Badge {
  id: string;
  name: string;
  description: string | null;
  /** Identifies which icon/asset the client renders — no image is stored here. */
  iconKey: string;
  criteriaType: BadgeCriteriaType;
  /** Meaning depends on criteriaType, e.g. "3" for programme_count means "complete 3 programmes". Unused for "custom". */
  criteriaValue: number;
  createdAt: string;
}
