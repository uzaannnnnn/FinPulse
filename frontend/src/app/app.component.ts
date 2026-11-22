import { Component, DestroyRef, inject, OnInit } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, NavigationEnd, Router, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs/operators';
import { AuthService } from './core/services/auth.service';
import { DEFAULT_SEO, SeoConfig, SeoService } from './core/services/seo.service';
import { ToastComponent } from './shared/components/toast/toast.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, ToastComponent],
  template: `
    <router-outlet />
    <app-toast />
  `,
})
export class AppComponent implements OnInit {
  private readonly authService = inject(AuthService);
  private readonly seoService = inject(SeoService);
  private readonly router = inject(Router);
  private readonly activatedRoute = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);

  ngOnInit(): void {
    this.applyActiveRouteSeo();

    this.router.events
      .pipe(
        filter((event): event is NavigationEnd => event instanceof NavigationEnd),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe(() => {
        this.applyActiveRouteSeo();
      });

    if (typeof window !== 'undefined' && this.authService.getAccessToken()) {
      this.authService
        .loadProfile()
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe({
          error: () => {
            // Handled by authInterceptor if 401; ignore network errors on background sync
          },
        });
    }
  }

  private applyActiveRouteSeo(): void {
    let currentRoute: ActivatedRoute | null = this.activatedRoute;
    while (currentRoute?.firstChild) {
      currentRoute = currentRoute.firstChild;
    }

    const seoData = currentRoute?.snapshot?.data?.['seo'] as SeoConfig | undefined;
    this.seoService.update(seoData ?? DEFAULT_SEO);
  }
}
