import { Component, OnInit } from '@angular/core';
import { Books } from '../_model/books';
import { Borrow } from '../_model/borrow';
import { BooksService } from '../_service/books.service';
import { BorrowService } from '../_service/borrow.service';
import { NotificationService } from '../_service/notification.service';
import { UserAuthService } from '../_service/user-auth.service';

@Component({
  selector: 'app-borrow-book',
  templateUrl: './borrow-book.component.html',
  styleUrls: ['./borrow-book.component.css']
})
export class BorrowBookComponent implements OnInit {

  books: Books[];

  constructor(
    private booksService: BooksService,
    private userAuthService: UserAuthService,
    private borrowService: BorrowService,
    private notifications: NotificationService,
  ) { }

  userId = this.userAuthService.getUserId();

  ngOnInit(): void {
    this.getBooks();
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

  borrow: Borrow = new Borrow();

  borrowBook(bookId: number) {
    this.borrow.bookId = bookId;
    this.borrow.userId = this.userId;
    this.borrowService.borrowBook(this.borrow).subscribe(data => {
      this.notifications.succes(
        'Book borrowed',
        'The loan has been registered.'
      );
    },
    error => this.notifications.refus(
      'Book not borrowed',
      this.notifications.messageErreurHttp(error, 'The server refused the borrow request.'),
      this.notifications.detailErreurHttp(error)
    ));
  }
}
