import { HttpErrorResponse, HttpEvent, HttpHandler, HttpInterceptor, HttpRequest } from '@angular/common/http';
import { Router } from '@angular/router';
import { catchError } from 'rxjs/operators';
import { Observable, throwError } from 'rxjs';
import { UserAuthService } from '../_service/user-auth.service';
import { Injectable } from '@angular/core';

@Injectable()
export class AuthInterceptor implements HttpInterceptor {
  constructor(
    private userAuthService: UserAuthService,
    private router:Router
  ) {}

  intercept(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    if (req.headers.get('No-Auth') === 'True') {
      return next.handle(req.clone());
    }

    const token = this.userAuthService.getToken();

    req = this.addToken(req, token);

    return next.handle(req).pipe(
        catchError(
            (err:HttpErrorResponse) => {
                console.log(err.status);
                if(err.status === 401) {
                    this.router.navigate(['/login']);
                } else if(err.status === 403) {
                    this.router.navigate(['/forbidden']);
                }
                // On relaie l'erreur d'origine au lieu de la remplacer par
                // une chaine fixe : sans elle, l'appelant perd le code HTTP et
                // le corps ApiError, donc le message que l'ecran des
                // reservations doit afficher pour un 400, un 404 ou un 409.
                return throwError(() => err);
            }
        )
    );
  }

  private addToken(request:HttpRequest<any>, token:string) {
      return request.clone(
          {
              setHeaders: {
                  Authorization : `Bearer ${token}`
              }
          }
      );
  }
}