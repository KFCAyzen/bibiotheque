import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Books } from '../_model/books';
import { BooksService } from '../_service/books.service';
import { NotificationService } from '../_service/notification.service';

@Component({
  selector: 'app-update-book',
  templateUrl: './update-book.component.html',
  styleUrls: ['./update-book.component.css']
})
export class UpdateBookComponent implements OnInit {

  bookId: number;
  book: Books = new Books();
  constructor(private booksService: BooksService,
    private notifications: NotificationService,
    private route: ActivatedRoute,
    private router: Router) { }

  ngOnInit(): void {
    this.bookId = this.route.snapshot.params['bookId'];
    this.booksService.getBookById(this.bookId).subscribe({
      next: data => {
        this.book = data;
      },
      error: error => this.notifications.refus(
        'Book not loaded',
        this.notifications.messageErreurHttp(error, 'The server refused the book request.'),
        this.notifications.detailErreurHttp(error)
      )
    });
  }

  onSubmit() {
    this.booksService.updateBook(this.bookId, this.book).subscribe( data =>{
        this.notifications.succes(
          'Book updated',
          `"${this.book.bookName}" has been updated.`
        );
        this.goToBooksList();
    },
    error => this.notifications.refus(
      'Book not updated',
      this.notifications.messageErreurHttp(error, 'The server refused the book request.'),
      this.notifications.detailErreurHttp(error)
    ));
  }

  goToBooksList() {
    this.router.navigate(['/books']);
  }

}
