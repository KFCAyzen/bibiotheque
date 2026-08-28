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
        this.messageFromError(error),
        this.detailFromError(error)
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
        this.messageFromError(error),
        this.detailFromError(error)
      );
    });
  }

  bookDetails(bookId: number) {
    this.router.navigate(['book-details', bookId ]);
  }

  private messageFromError(error: any): string {
    return error?.error?.message
      || error?.error
      || 'The server refused the book request.';
  }

  private detailFromError(error: any): string | null {
    return error?.status ? `HTTP ${error.status}` : null;
  }

}
