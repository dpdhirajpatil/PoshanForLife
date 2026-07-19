/** Catalogue (Programmes / Sessions / Challenges) — mirrors CatalogueItemDto. */

export type CatalogueType = 'programmes' | 'sessions' | 'challenges';

export type CatalogueStatus = 'draft' | 'published' | 'archived';

export const CATALOGUE_STATUSES: CatalogueStatus[] = ['draft', 'published', 'archived'];

export interface CatalogueItem {
  id: string;
  name: string;
  serviceCode: string;
  /** Free-text sub-category (e.g. "Weight loss", "Yoga"). */
  type: string;
  priceInr: number;
  description?: string;
  coverImageUrl?: string;
  status: CatalogueStatus;
  /** Programmes only. */
  durationWeeks?: number;
  /** Sessions only. */
  durationMinutes?: number;
  /** Challenges only. */
  durationDays?: number;
  /** Challenges only. */
  goalDescription?: string;
  /** Active patient assignments referencing this item (blocks deletion). */
  activeAssignmentCount: number;
  createdBy?: { id: string; name: string };
  createdAt: string;
  updatedAt: string;
}

/** Per-type labels used by the shared table/form components. */
export const CATALOGUE_TYPE_META: Record<
  CatalogueType,
  { singular: string; plural: string; durationField: 'durationWeeks' | 'durationMinutes' | 'durationDays'; durationLabel: string; durationUnit: string }
> = {
  programmes: {
    singular: 'programme',
    plural: 'Programmes',
    durationField: 'durationWeeks',
    durationLabel: 'Duration (weeks)',
    durationUnit: 'wk',
  },
  sessions: {
    singular: 'session',
    plural: 'Sessions',
    durationField: 'durationMinutes',
    durationLabel: 'Duration (minutes)',
    durationUnit: 'min',
  },
  challenges: {
    singular: 'challenge',
    plural: 'Challenges',
    durationField: 'durationDays',
    durationLabel: 'Duration (days)',
    durationUnit: 'days',
  },
};
