import { Routes } from '@angular/router';
import { PatientDetailPageComponent } from './patient-detail-page.component';
import { PatientsPageComponent } from './patients-page.component';

export default [
  { path: '', component: PatientsPageComponent, title: 'Poshan · Patients' },
  { path: ':id', component: PatientDetailPageComponent, title: 'Poshan · Patient' },
] satisfies Routes;
