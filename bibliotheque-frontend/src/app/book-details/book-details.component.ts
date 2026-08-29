import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Books } from '../_model/books';
import { Borrow } from '../_model/borrow';
import { Users } from '../_model/users';
import { BooksService } from '../_service/books.service';
import { BorrowService } from '../_service/borrow.service';
import { NotificationService } from '../_service/notification.service';
import { UsersService } from '../_service/users.service';

@Component({
  selector: 'app-book-details',
  templateUrl: './book-details.component.html',
  styleUrls: ['./book-details.component.css']
})
export class BookDetailsComponent implements OnInit {

  id: number;
  book: Books;
  borrow: Borrow[];
  user: Users;

  constructor(private route: ActivatedRoute,
    private bookService: BooksService,
    private borrowService: BorrowService,
    private notifications: NotificationService,
    public userService: UsersService
  ) { }

  ngOnInit(): void {
    this.id = this.route.snapshot.params['bookId'];
    // console.log(this.id);
    this.book = new Books();
    this.bookService.getBookById(this.id).subscribe({
      next: data => {
        this.book = data;
      },
      error: error => this.notifications.refus(
        'Book not loaded',
        this.notifications.messageErreurHttp(error, 'The server refused the book request.'),
        this.notifications.detailErreurHttp(error)
      )
    });

    this.getBorrowHistory(this.id);
    
  }

  private getBorrowHistory(bookId: number) {
    this.borrowService.getBookBorrowHistory(bookId).subscribe({
      next: data => {
        this.borrow = data;
      },
      error: error => this.notifications.refus(
        'History not loaded',
        this.notifications.messageErreurHttp(error, 'The server refused the history request.'),
        this.notifications.detailErreurHttp(error)
      )
    });
  }

  public getUserData(userId: number):string {
    this.user = new Users();
    this.userService.getUserById(userId).subscribe({
      next: data => {
        this.user = data;
      },
      error: error => this.notifications.refus(
        'User not loaded',
        this.notifications.messageErreurHttp(error, 'The server refused the user request.'),
        this.notifications.detailErreurHttp(error)
      )
    });
    return this.user.name;
  }
}
