import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { Books } from '../_model/books'
import { BooksService } from '../_service/books.service';
import { NotificationService } from '../_service/notification.service';

@Component({
  selector: 'app-books-list',
  templateUrl: './books-list.component.html',
  styleUrls: ['./books-list.component.css']
})
export class BooksListComponent implements OnInit {

  books: Books[];

  constructor(private booksService: BooksService,
    private notifications: NotificationService,
    private router: Router) { }

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

  updateBook(bookId: number) {
    this.router.navigate(['update-book', bookId ]);
  }

  deleteBook(bookId: number) {
    this.booksService.deleteBook(bookId).subscribe( data=> {
      this.notifications.succes(
        'Book deleted',
        'The book has been removed from the catalogue.'
      );
      this.getBooks();
    },
    error => {
      this.notifications.refus(
        'Book not deleted',
        this.notifications.messageErreurHttp(error, 'The server refused the book request.'),
        this.notifications.detailErreurHttp(error)
      );
    });
  }

  bookDetails(bookId: number) {
    this.router.navigate(['book-details', bookId ]);
  }

}
