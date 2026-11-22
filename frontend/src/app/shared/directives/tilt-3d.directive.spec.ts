import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Tilt3dDirective } from './tilt-3d.directive';

@Component({
  standalone: true,
  imports: [Tilt3dDirective],
  template: `<div data-testid="card" appTilt3d style="width: 200px; height: 120px;">Card</div>`,
})
class TestTiltHostComponent {}

describe('Tilt3dDirective', () => {
  it('should add fp-tilt-host class and reset transform on mouseleave', () => {
    TestBed.configureTestingModule({
      imports: [TestTiltHostComponent],
    });

    const fixture = TestBed.createComponent(TestTiltHostComponent);
    fixture.detectChanges();

    const card = fixture.nativeElement.querySelector('[data-testid="card"]') as HTMLElement;
    expect(card.classList.contains('fp-tilt-host')).toBeTrue();

    card.dispatchEvent(new MouseEvent('mouseleave'));
    expect(card.style.transform).toContain('rotateX(0deg)');
    expect(card.style.transform).toContain('rotateY(0deg)');
    fixture.destroy();
  });
});
