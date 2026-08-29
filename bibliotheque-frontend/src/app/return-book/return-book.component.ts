import { Component, OnInit } from '@angular/core';
import { Books } from '../_model/books';
import { Borrow } from '../_model/borrow';
import { BooksService } from '../_service/books.service';
import { BorrowService } from '../_service/borrow.service';
import { NotificationService } from '../_service/notification.service';
import { UserAuthService } from '../_service/user-auth.service';

@Component({
  selector: 'app-return-book',
  templateUrl: './return-book.component.html',
  styleUrls: ['./return-book.component.css']
})
export class ReturnBookComponent implements OnInit {

  books: Books[];
  borrow: Borrow[];

  constructor(
    private borrowService: BorrowService,
    private booksService: BooksService,
    private userAuthService: UserAuthService,
    private notifications: NotificationService
  ) { }

  userId = this.userAuthService.getUserId();

  ngOnInit(): void {
    this.getBooks();
    this.getBooksByUser();
  }

  private getBooks() {
    this.booksService.getBooksList().subscribe({
      next: data =>{
        this.books = data;
      },
      error: error => this.notifications.refus(
        'Books not loaded',
        this.notifications.messageErreurHttp(error, 'The server refused the book request.'),
        this.notifications.detailErreurHttp(error)
      )
    });
  }

  
  private getBooksByUser() {
    this.borrowService.getBooksBorrowedByUser(this.userId).subscribe({
      next: data => {
        this.borrow = data;
      },
      error: error => this.notifications.refus(
        'Loans not loaded',
        this.notifications.messageErreurHttp(error, 'The server refused the loan request.'),
        this.notifications.detailErreurHttp(error)
      )
    });
  }

  brw: Borrow = new Borrow();
  public returnBook(borrowId: number) {
    this.brw.borrowId = borrowId;
    this.borrowService.returnBook(this.brw).subscribe(data => {
      this.notifications.succes(
        'Book returned',
        'The return has been registered.'
      );
      this.getBooksByUser();
    },
    error => this.notifications.refus(
      'Book not returned',
      this.notifications.messageErreurHttp(error, 'The server refused the return request.'),
      this.notifications.detailErreurHttp(error)
    ));
  }

}
