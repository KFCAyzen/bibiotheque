import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { Books } from '../_model/books';
import { BooksService } from '../_service/books.service';
import { NotificationService } from '../_service/notification.service';

@Component({
  selector: 'app-create-book',
  templateUrl: './create-book.component.html',
  styleUrls: ['./create-book.component.css']
})
export class CreateBookComponent implements OnInit {

  book: Books = new Books();
  constructor(private booksService: BooksService,
    private notifications: NotificationService,
    private router: Router) { }

  ngOnInit(): void {
  }

  saveBook() {
    this.booksService.createBook(this.book).subscribe(data => {
      this.notifications.succes(
        'Book created',
        `"${this.book.bookName}" has been added to the catalogue.`
      );
      this.goToBooksList();
    },
    error => this.notifications.refus(
      'Book not created',
      this.notifications.messageErreurHttp(error, 'The server refused the book creation request.'),
      this.notifications.detailErreurHttp(error)
    ));
  }

  goToBooksList() {
    this.router.navigate(['/books']);
  }

  onSubmit() {
    this.saveBook();
  }

}
