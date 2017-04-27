package com.hijacker;

/*
    Copyright (C) 2016  Christos Kyriakopoylos

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>
 */

import static com.hijacker.CustomAction.TYPE_AP;
import static com.hijacker.CustomAction.TYPE_ST;
import static com.hijacker.Device.getByMac;

class UpdateRequest {
    int i1, i2, i3, i4 ,i5;
    int type;
    String str1, str2, str3, str4, str5;
    UpdateRequest(String str1, String str2, String str3, String str4, String str5, int i1, int i2, int i3, int i4, int i5){
        //AP
        this.str1 = str1;
        this.str2 = str2;
        this.str3 = str3;
        this.str4 = str4;
        this.str5 = str5;
        this.i1 = i1;
        this.i2 = i2;
        this.i3 = i3;
        this.i4 = i4;
        this.i5 = i5;
        this.type = TYPE_AP;
    }
    UpdateRequest(String str1, String str2, String str3, int i1, int i2, int i3){
        //ST
        this.str1 = str1;
        this.str2 = str2;
        this.str3 = str3;
        this.i1 = i1;
        this.i2 = i2;
        this.i3 = i3;
        this.type = TYPE_ST;
    }
    void add(){
        if(this.type==TYPE_AP){
            //AP
            AP temp = (AP)getByMac(str2);
            if(temp==null) new AP(str1, str2, str3, str4, str5, i1, i2, i3, i4, i5);
            else temp.update(str1, str3, str4, str5, i1, i2, i3, i4, i5);
        }else{
            //ST
            ST temp = (ST)getByMac(str1);
            if (str2.equals("na")) str2=null;
            if (temp == null) new ST(str1, str2, i1, i2, i3, str3);
            else temp.update(str2, i1, i2, i3, str3);
        }
    }
}
