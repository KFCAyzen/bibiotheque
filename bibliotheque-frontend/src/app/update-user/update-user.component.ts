import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Users } from '../_model/users';
import { NotificationService } from '../_service/notification.service';
import { UsersService } from '../_service/users.service';

@Component({
  selector: 'app-update-user',
  templateUrl: './update-user.component.html',
  styleUrls: ['./update-user.component.css']
})
export class UpdateUserComponent implements OnInit {

  userId: number;
  user: Users = new Users();
  constructor(private usersService: UsersService,
    private notifications: NotificationService,
    private route: ActivatedRoute,
    private router: Router) { }

  ngOnInit(): void {
    this.userId = this.route.snapshot.params['userId'];
    this.usersService.getUserById(this.userId).subscribe({
      next: data => {
        this.user = data;
      },
      error: error => this.notifications.refus(
        'User not loaded',
        this.messageFromError(error),
        this.detailFromError(error)
      )
    });
  }

  onSubmit() {
    this.usersService.updateUser(this.userId, this.user).subscribe( data =>{
        this.notifications.succes(
          'User updated',
          `${this.user.name || this.user.username} has been updated.`
        );
        this.goToUsersList();
    },
    error => this.notifications.refus(
      'User not updated',
      this.messageFromError(error),
      this.detailFromError(error)
    ));
  }

  goToUsersList() {
    this.router.navigate(['/users']);
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
