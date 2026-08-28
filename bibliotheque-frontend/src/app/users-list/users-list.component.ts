import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { Users } from '../_model/users';
import { NotificationService } from '../_service/notification.service';
import { UsersService } from '../_service/users.service';

@Component({
  selector: 'app-users-list',
  templateUrl: './users-list.component.html',
  styleUrls: ['./users-list.component.css']
})
export class UsersListComponent implements OnInit {

  users: Users[];

  constructor(private usersService: UsersService,
    private notifications: NotificationService,
    private router: Router) { }

  ngOnInit(): void {
    this.getUsers();
    // this.users = [{
    //   "userId": 1,
    //   "name": "tarun",
    //   "username": "tarungowda",
    //   "role": "STUDENT",
    //   "password": "sdklfjlakdsf"
    // }]
  }

  private getUsers() {
    this.usersService.getUsersList().subscribe({
      next: data =>{
        this.users = data;
      },
      error: error => this.notifications.refus(
        'Users not loaded',
        this.messageFromError(error),
        this.detailFromError(error)
      )
    });
  }

  userDetails(userId: number) {
    this.router.navigate(['user-details', userId ]);
  }

  updateUser(userId: number) {
    this.router.navigate(['update-user', userId ]);
  }

  private messageFromError(error: any): string {
    return error?.error?.message
      || error?.error
      || 'The server refused the user request.';
  }

  private detailFromError(error: any): string | null {
    return error?.status ? `HTTP ${error.status}` : null;
  }

}
