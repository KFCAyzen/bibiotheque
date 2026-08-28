import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { Books } from '../_model/books';
import { ApiService } from './api.service';

@Injectable({
  providedIn: 'root'
})
export class BooksService {

  private readonly basePath = '/admin/books';

  constructor(private api: ApiService) { }

  getBooksList(): Observable<Books[]> {
    return this.api.get<Books[]>(this.basePath);
  }

  createBook(book: Books): Observable<Object> {
    return this.api.post<Object>(this.basePath, book);
  }

  getBookById(bookId: number): Observable<Books> {
    return this.api.get<Books>(`${this.basePath}/${bookId}`);
  }

  updateBook(bookId: number, book: Books): Observable<Object> {
    return this.api.put<Object>(`${this.basePath}/${bookId}`, book);
  }

  deleteBook(bookId: number): Observable<Object> {
    return this.api.delete<Object>(`${this.basePath}/${bookId}`);
  }
}
