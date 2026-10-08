// SPDX-License-Identifier: GPL-2.0
package dev.codex.vowifi.tool;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import java.util.*;
import java.util.regex.*;
import org.json.*;

/** Explicit user-requested APN fields only; never queries subscriber identifiers. */
final class ApnDetails {
    static final String[] FIELDS={"name","apn","user","password","mmsc","mmsproxy","mmsport","mcc","mnc","type","protocol","roaming_protocol","authtype","carrier_enabled"};
    static JSONObject read(Context context,int slot,int sub,String plmn)throws Exception{
        RootDiagnostics.requireSmsOwner(context,slot,sub);
        JSONObject result=new JSONObject().put("schema",1);
        String preferred="content://telephony/carriers/preferapn/subId/"+sub;
        try{JSONArray rows=query(context,preferred,null);result.put("preferred",rows.length()>0?rows.getJSONObject(0):JSONObject.NULL).put("preferred_status",rows.length()>0?"OBSERVED":"NOT_SET");}
        catch(Exception e){result.put("preferred_status","UNAVAILABLE").put("preferred_error",e.getClass().getSimpleName());}
        if(plmn!=null&&plmn.matches("[0-9]{5,6}")){
            String where="numeric='"+plmn+"' AND (type='*' OR type='ims' OR type LIKE 'ims,%' OR type LIKE '%,ims' OR type LIKE '%,ims,%')";
            try{result.put("ims_records",query(context,"content://telephony/carriers/subId/"+sub,where)).put("ims_status","OBSERVED");}
            catch(Exception e){result.put("ims_status","UNAVAILABLE").put("ims_error",e.getClass().getSimpleName());}
        }else result.put("ims_status","NO_OPERATOR");
        RootDiagnostics.requireSmsOwner(context,slot,sub);return result;
    }
    private static JSONArray query(Context context,String uri,String where)throws Exception{
        try(Cursor cursor=context.getContentResolver().query(Uri.parse(uri),FIELDS,where,null,null)){
            if(cursor==null)throw new IllegalStateException("apn-provider-unavailable");JSONArray rows=new JSONArray();
            while(cursor.moveToNext()&&rows.length()<4){JSONObject row=new JSONObject();for(String field:FIELDS){int index=cursor.getColumnIndex(field);if(index>=0)row.put(field,cursor.isNull(index)?"":cursor.getString(index));}rows.put(row);}return rows;
        }catch(Exception attributionFailure){
            ArrayList<String> command=new ArrayList<>(Arrays.asList("content","query","--uri",uri,"--projection",String.join(":",FIELDS)));
            if(where!=null){command.add("--where");command.add(where);}
            return parseCli(RootDiagnostics.command(3,command.toArray(new String[0])));
        }
    }
    static JSONArray parseCli(String output)throws Exception{
        JSONArray rows=new JSONArray();if(output.trim().equals("No result found."))return rows;
        String names=String.join("|",FIELDS);
        Pattern values=Pattern.compile("(?:^|, )("+names+")=");
        for(String line:output.split("[\\r\\n]+")){
            if(!line.matches("Row: [0-9]+ .*"))continue;
            String data=line.substring(line.indexOf(' ',5)+1);Matcher match=values.matcher(data);
            ArrayList<String> fields=new ArrayList<>();ArrayList<Integer> starts=new ArrayList<>(),ends=new ArrayList<>();
            while(match.find()){fields.add(match.group(1));starts.add(match.end());ends.add(match.start());}
            JSONObject row=new JSONObject();
            for(int i=0;i<fields.size();i++){
                if(row.has(fields.get(i)))throw new IllegalStateException("ambiguous-apn-cli-fields");
                String value=data.substring(starts.get(i),i+1<fields.size()?ends.get(i+1):data.length());
                row.put(fields.get(i),"NULL".equalsIgnoreCase(value)?"":value);
            }
            // Reject errors/truncation instead of inventing unset credentials.
            if(row.length()!=FIELDS.length)throw new IllegalStateException("incomplete-apn-cli-fields");
            if(rows.length()<4)rows.put(row);
        }
        if(rows.length()==0)throw new IllegalStateException("apn-cli-result-unavailable");return rows;
    }
    static String describe(JSONObject row,boolean revealPassword){
        if(row==null)return "未设置或不可见";
        String[] labels={"名称","APN","用户名","密码","MMSC","MMS Proxy","MMS Port","MCC","MNC","APN 类型","协议","漫游协议","认证类型","已启用"};
        StringBuilder text=new StringBuilder();for(int i=0;i<FIELDS.length;i++){
            if(i>0)text.append('\n');text.append(labels[i]).append(": ");String field=FIELDS[i];
            if(!row.has(field)||row.isNull(field))text.append("不可见");
            else if(row.optString(field).isEmpty())text.append("未设置");
            else if("password".equals(field)&&!revealPassword)text.append("••••（点击查看）");
            else text.append(row.optString(field));
        }return text.toString();
    }
}
