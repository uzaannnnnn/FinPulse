import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import { CategoryDto } from '../models/category.model';
import { CategoryService } from './category.service';

describe('CategoryService', () => {
  let service: CategoryService;
  let httpMock: HttpTestingController;

  const mockCategories: CategoryDto[] = [
    {
      id: '660e8400-e29b-41d4-a716-446655440001',
      name: 'Gaji',
      type: 'INCOME',
      icon: 'wallet',
      color: '#10B981',
      isDefault: true,
    },
    {
      id: '660e8400-e29b-41d4-a716-446655440002',
      name: 'Makanan & Minuman',
      type: 'EXPENSE',
      icon: 'utensils',
      color: '#F59E0B',
      isDefault: true,
    },
  ];

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(CategoryService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should fetch all categories without type query param', () => {
    let result: CategoryDto[] | undefined;
    service.getCategories().subscribe((data) => (result = data));

    const req = httpMock.expectOne(`${environment.apiUrl}/categories`);
    expect(req.request.method).toBe('GET');
    expect(req.request.params.has('type')).toBeFalse();
    req.flush(mockCategories);

    expect(result?.length).toBe(2);
  });

  it('should pass type query param when filtering categories', () => {
    let result: CategoryDto[] | undefined;
    service.getCategories('EXPENSE').subscribe((data) => (result = data));

    const req = httpMock.expectOne(
      (r) => r.url === `${environment.apiUrl}/categories` && r.params.get('type') === 'EXPENSE'
    );
    expect(req.request.method).toBe('GET');
    req.flush([mockCategories[1]]);

    expect(result).toEqual([mockCategories[1]]);
  });
});
