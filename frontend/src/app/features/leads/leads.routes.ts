import { Routes } from '@angular/router';
import { LeadDetailPageComponent } from './lead-detail-page.component';
import { LeadsPageComponent } from './leads-page.component';

export default [
  { path: '', component: LeadsPageComponent, title: 'Poshan · Contacts' },
  { path: ':id', component: LeadDetailPageComponent, title: 'Poshan · Lead' },
] satisfies Routes;
