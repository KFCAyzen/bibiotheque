import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { Users } from '../_model/users';
import { NotificationService } from '../_service/notification.service';
import { UsersService } from '../_service/users.service';

@Component({
  selector: 'app-registration',
  templateUrl: './registration.component.html',
  styleUrls: ['./registration.component.css']
})
export class RegistrationComponent implements OnInit {

  user: Users = new Users();
  constructor(private usersService: UsersService,
    private notifications: NotificationService,
    private router: Router) { }

  ngOnInit(): void {
  }

  saveUser() {
    this.usersService.createUser(this.user).subscribe(data => {
      this.notifications.succes(
        'User created',
        `${this.user.name || this.user.username} has been added.`
      );
      this.goToUsersList();
    },
    error => this.notifications.refus(
      'User not created',
      this.notifications.messageErreurHttp(error, 'The server refused the user creation request.'),
      this.notifications.detailErreurHttp(error)
    ));
  }

  goToUsersList() {
    this.router.navigate(['/users']);
  }

  onSubmit() {
    this.saveUser();
  }

}
