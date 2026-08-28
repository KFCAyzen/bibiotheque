import { Injectable } from '@angular/core';
import { NgForm } from '@angular/forms';
import { Observable } from 'rxjs';
import { Users } from '../_model/users';
import { ApiService } from './api.service';
import { UserAuthService } from './user-auth.service';

@Injectable({
  providedIn: 'root'
})
export class UsersService {

  private readonly basePath = '/admin/users';

  constructor(
    private api: ApiService,
    private userAuthService: UserAuthService
  ) { }

  public login(loginData: NgForm) {
    return this.api.post('/authenticate', loginData, {
      headers: this.api.noAuthHeaders,
    });
  }

  public roleMatch(allowedRoles: any): boolean {
    let isMatch = false;
    const userRoles: any = this.userAuthService.getRoles();

    if (userRoles != null && userRoles) {
      for (let i = 0; i < userRoles.length; i++) {
        for (let j = 0; j < allowedRoles.length; j++) {
          if (userRoles[i].roleName === allowedRoles[j]) {
            isMatch = true;
            return isMatch;
          } else {
            return isMatch;
          }
        }
      }
    }

    return false;
  }

  getUsersList(): Observable<Users[]> {
    return this.api.get<Users[]>(this.basePath);
  }

  createUser(user: Users): Observable<Object> {
    return this.api.post<Object>(this.basePath, user);
  }

  getUserById(userId: number): Observable<Users> {
    return this.api.get<Users>(`${this.basePath}/${userId}`);
  }

  updateUser(userId: number, user: Users): Observable<Object> {
    return this.api.put<Object>(`${this.basePath}/${userId}`, user);
  }

}
