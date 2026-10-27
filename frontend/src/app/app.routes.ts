import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'book' },
  {
    path: 'book',
    title: 'Book an appointment',
    loadComponent: () => import('./features/book/book.page').then((m) => m.BookPage),
  },
  {
    path: 'confirmation',
    title: 'Appointment booked',
    loadComponent: () =>
      import('./features/confirmation/confirmation.page').then((m) => m.ConfirmationPage),
  },
  {
    path: 'manage',
    title: 'Manage your booking',
    loadComponent: () => import('./features/manage/manage.page').then((m) => m.ManagePage),
  },
  { path: '**', redirectTo: 'book' },
];
