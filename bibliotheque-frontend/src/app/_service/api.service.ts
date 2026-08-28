import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class ApiService {

  private readonly apiUrl = environment.apiUrl.replace(/\/$/, '');

  readonly noAuthHeaders = new HttpHeaders({ 'No-Auth': 'True' });

  constructor(private httpClient: HttpClient) { }

  url(path: string): string {
    const normalizedPath = path.startsWith('/') ? path : `/${path}`;
    return `${this.apiUrl}${normalizedPath}`;
  }

  get<T>(path: string, options?: object): Observable<T> {
    return this.httpClient.get<T>(this.url(path), options);
  }

  post<T>(path: string, body: unknown, options?: object): Observable<T> {
    return this.httpClient.post<T>(this.url(path), body, options);
  }

  put<T>(path: string, body: unknown, options?: object): Observable<T> {
    return this.httpClient.put<T>(this.url(path), body, options);
  }

  patch<T>(path: string, body: unknown, options?: object): Observable<T> {
    return this.httpClient.patch<T>(this.url(path), body, options);
  }

  delete<T>(path: string, options?: object): Observable<T> {
    return this.httpClient.delete<T>(this.url(path), options);
  }
}
