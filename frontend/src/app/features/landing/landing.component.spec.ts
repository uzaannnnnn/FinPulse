import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { LandingComponent } from './landing.component';

describe('LandingComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [LandingComponent],
      providers: [provideRouter([])],
    }).compileComponents();
  });

  it('should create landing page component', () => {
    const fixture = TestBed.createComponent(LandingComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should render semantic landmarks with exactly one h1 and ordered headings', () => {
    const fixture = TestBed.createComponent(LandingComponent);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;

    expect(el.querySelector('header')).not.toBeNull();
    expect(el.querySelector('main')).not.toBeNull();
    expect(el.querySelector('footer')).not.toBeNull();

    const h1List = el.querySelectorAll('h1');
    expect(h1List.length).toBe(1);
    expect(h1List[0].textContent?.trim().length).toBeGreaterThan(10);

    expect(el.querySelectorAll('h2').length).toBeGreaterThanOrEqual(3);
    expect(el.querySelectorAll('h3').length).toBe(7);
  });

  it('should render login and register CTA links', () => {
    const fixture = TestBed.createComponent(LandingComponent);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;

    const registerLinks = el.querySelectorAll('a[href="/register"]');
    const loginLinks = el.querySelectorAll('a[href="/login"]');

    expect(registerLinks.length).toBeGreaterThanOrEqual(3);
    expect(loginLinks.length).toBeGreaterThanOrEqual(3);
  });

  it('should not expose forbidden technical jargon in public copy', () => {
    const fixture = TestBed.createComponent(LandingComponent);
    fixture.detectChanges();
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';

    const forbiddenTerms = [
      'Redis',
      'Cache',
      'RabbitMQ',
      'Queue',
      'Worker',
      'Async',
      'Polling',
      'JWT',
      'Token',
      'API',
      'Endpoint',
      'Supabase',
      'Sprint',
      'HTTP',
    ];

    for (const term of forbiddenTerms) {
      const regex = new RegExp(`\\b${term}\\b`, 'i');
      expect(regex.test(text)).toBeFalse();
    }
  });
});
