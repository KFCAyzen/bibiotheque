import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { Borrow } from '../_model/borrow';
import { ApiService } from './api.service';

@Injectable({
  providedIn: 'root'
})
export class BorrowService {

  private readonly basePath = '/borrow';

  constructor(private api: ApiService) { }

  getBorrowList(): Observable<Borrow[]> {
    return this.api.get<Borrow[]>(this.basePath);
  }

  borrowBook(borrow: Borrow): Observable<Object> {
    return this.api.post<Object>(this.basePath, borrow);
  }

  returnBook(borrow: Borrow): Observable<Object> {
    return this.api.put<Object>(this.basePath, borrow);
  }

  getBooksBorrowedByUser(userId: number): Observable<Borrow[]> {
    return this.api.get<Borrow[]>(`${this.basePath}/user/${userId}`);
  }

  getBookBorrowHistory(bookId: number): Observable<Borrow[]> {
    return this.api.get<Borrow[]>(`${this.basePath}/book/${bookId}`);
  }
}
