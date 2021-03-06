package com.zhicloud.ms.controller;

import com.zhicloud.ms.app.pool.computePool.ComputeInfoExt;
import com.zhicloud.ms.app.pool.computePool.ComputeInfoPool;
import com.zhicloud.ms.app.pool.computePool.ComputeInfoPoolManager;
import com.zhicloud.ms.app.pool.host.migration.MigrationProgressData;
import com.zhicloud.ms.app.pool.host.migration.MigrationProgressPool;
import com.zhicloud.ms.app.pool.host.migration.MigrationProgressPoolManager;
import com.zhicloud.ms.app.pool.storage.StorageManager;
import com.zhicloud.ms.app.pool.storage.StorageResult;
import com.zhicloud.ms.constant.AppInconstant;
import com.zhicloud.ms.constant.MonitorConstant;
import com.zhicloud.ms.exception.AppException;
import com.zhicloud.ms.httpGateway.HttpGatewayAsyncChannel;
import com.zhicloud.ms.httpGateway.HttpGatewayChannelExt;
import com.zhicloud.ms.httpGateway.HttpGatewayManager;
import com.zhicloud.ms.httpGateway.HttpGatewayResponseHelper;
import com.zhicloud.ms.remote.MethodResult;
import com.zhicloud.ms.service.*;
import com.zhicloud.ms.transform.constant.TransFormPrivilegeConstant;
import com.zhicloud.ms.transform.util.TransFormPrivilegeUtil;
import com.zhicloud.ms.util.StringUtil;
import com.zhicloud.ms.vo.*;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.log4j.Logger;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


@Controller
@RequestMapping("/cdrpm")
public class ResourcePoolController {
 
    public static final Logger logger = Logger.getLogger(ResourcePoolController.class);

    @Resource
    private ICloudHostService cloudHostService;
    
    @Resource
    private IHostMigrateionService hostMigrateionService;

    @Resource
    private IComputePoolService computePoolService;
    
    @Resource
    private ICloudHostWarehouseService  CloudHostWarehouseService;

    @Resource
    private IOperLogService operLogService;

    @Resource
    private SharedMemoryService sharedMemoryService;

 
    /**
     * 查询所有资源池
     * @param model
     * @param request
     * @return
     */
    @RequestMapping(value="/all",method=RequestMethod.GET)
    public String getAll(Model model,HttpServletRequest request){
        if( ! new TransFormPrivilegeUtil().isHasPrivilege(request, TransFormPrivilegeConstant.desktop_resource_pool_query)){
            return "not_have_access";
        }
        try {
            List<ComputeInfoExt> cList = new ArrayList<>();
                HttpGatewayChannelExt channel = HttpGatewayManager.getChannel(1);
                if(channel!=null){
                    JSONObject result = channel.computePoolQuery();
            if ("fail".equals(result.getString("status"))){
                logger.error("ResourcePoolController.getAll>>>获取资源池失败");
                return "not_responsed";
            }
            ComputeInfoPool  pool = ComputeInfoPoolManager.singleton().getPool();

            JSONArray computerList = result.getJSONArray("compute_pools");
            for (int i = 0; i < computerList.size(); i ++) {
                JSONObject computerObject = computerList.getJSONObject(i);
                String uuid = computerObject.getString("uuid");
                String name = computerObject.getString("name");
                if(!name.contains("desktop_pool")){
                    continue;
                }
                int status = computerObject.getInt("status");
                Integer cpuCount = computerObject.getInt("cpu_count");
                String hostCount = computerObject.getString("host");
                String host = hostCount.substring(1, hostCount.length()-1);
                String[] num = host.split(",");
                int hostNumber =0;
                for(int q=0;q<num.length-1;q++){
                	hostNumber =hostNumber+Integer.valueOf(num[q]);
                }
                BigDecimal cpuUsage = new BigDecimal(computerObject.getString("cpu_usage"));
                BigDecimal memoryUsage = new BigDecimal(computerObject.getString("memory_usage"));
                BigDecimal diskUsage = new BigDecimal(computerObject.getString("disk_usage"));
                
                JSONArray memoryList = computerObject.getJSONArray("memory");
                BigInteger[] mcount = new BigInteger[memoryList.size()];
                for(int j=0;j<memoryList.size();j++){
                    mcount[j] = new BigInteger(memoryList.getString(j));
                }
                
                JSONArray diskList = computerObject.getJSONArray("disk_volume");
                BigInteger[] dcount = new BigInteger[diskList.size()];
                for(int j=0;j<diskList.size();j++){
                    dcount[j] = new BigInteger(diskList.getString(j));
                }
                
                JSONArray nList = computerObject.getJSONArray("node");
                Integer[] ncount = new Integer[nList.size()];
                for(int j=0;j<nList.size();j++){
                    ncount[j] = nList.getInt(j);
                }
                
                JSONArray hList = computerObject.getJSONArray("host");
                Integer[] hcount = new Integer[hList.size()];
                for(int j=0;j<hList.size();j++){
                    hcount[j] = hList.getInt(j);
                }

                ComputeInfoExt computer = computePoolService.getComputePoolDetailSync(uuid);
                if(computer ==null){
                    continue;
                }
                computer.setCpuCount(cpuCount);
                computer.setCpuUsage(cpuUsage);
                computer.setDiskUsage(diskUsage);
                computer.setDiskVolume(dcount);
                computer.setHost(hcount);
                computer.setMemory(mcount);
                computer.setMemoryUsage(memoryUsage);
                computer.setName(name);
                computer.setNode(ncount);
                computer.setStatus(status);
                computer.setUuid(uuid);
                computer.setRegion(1);
                computer.setHostCount(hostNumber+"");
                cList.add(computer);
                pool.putToComputePool(uuid, computer);
            }
        }
            
            model.addAttribute("computerPool", cList);
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "resource_pool_manage";
    }
    
    /**
     * 跳转到资源(节点)列表页
     * @param uuid
     * @param curNum 主机资源池节点下的电脑数量
     * @param model
     * @param request
     * @return
     */
    @RequestMapping(value="/{uuid}/rd",method=RequestMethod.GET)
    public String resourceDetail(@PathVariable("uuid") String uuid,Model model,HttpServletRequest request){
        if( ! new TransFormPrivilegeUtil().isHasPrivilege(request, TransFormPrivilegeConstant.desktop_resource_node_query)){
            return "not_have_access";
        }
        String searchName = request.getParameter("sn");
        String curNum = request.getParameter("curNum");
        
        try {
            List<ComputeInfoExt> cList = new ArrayList<>();
            List<ComputeInfoExt> curList = new ArrayList<>();
            String flag = "no";
                HttpGatewayChannelExt channel = HttpGatewayManager.getChannel(1);
                if(channel!=null){
                    JSONObject result = channel.computePoolQueryResource(uuid);
            if ("fail".equals(result.getString("status"))){
                logger.error("ResourcePoolController.resourceDetail>>>获取资源池失败");
                return "not_responsed";
            }
                    JSONArray computerList = result.getJSONArray("compute_resources");
                    for (int i = 0; i < computerList.size(); i ++) {
                        JSONObject computerObject = computerList.getJSONObject(i);
                        String name = computerObject.getString("name");
                        String ip = computerObject.getString("ip");
                        int status = computerObject.getInt("status");
                        Integer cpuCount = computerObject.getInt("cpu_count");
                        String hostCount = computerObject.getString("host");
                        BigDecimal cpuUsage = new BigDecimal(computerObject.getString("cpu_usage"));
                        BigDecimal memoryUsage = new BigDecimal(computerObject.getString("memory_usage"));
                        BigDecimal diskUsage = new BigDecimal(computerObject.getString("disk_usage"));
                        
                        JSONArray memoryList = computerObject.getJSONArray("memory");
                        BigInteger[] mcount = new BigInteger[memoryList.size()];
                        for(int j=0;j<memoryList.size();j++){
                            mcount[j] = new BigInteger(memoryList.getString(j));
                        }
                        
                        JSONArray diskList = computerObject.getJSONArray("disk_volume");
                        BigInteger[] dcount = new BigInteger[diskList.size()];
                        for(int j=0;j<diskList.size();j++){
                            dcount[j] = new BigInteger(diskList.getString(j));
                        }
                        
                        ComputeInfoExt computer = new ComputeInfoExt();
                        computer.setCpuCount(cpuCount);
                        computer.setCpuUsage(cpuUsage);
                        computer.setDiskUsage(diskUsage);
                        computer.setDiskVolume(dcount);
                        computer.setMemory(mcount);
                        computer.setMemoryUsage(memoryUsage);
                        computer.setName(name);
                        computer.setStatus(status);
                        computer.setIp(ip);
                        computer.setHostCount(hostCount);
                        cList.add(computer);
                    }
                    if(searchName!=null && searchName!="" && cList.size()>0){
                        for(ComputeInfoExt cp : cList){
                            if(cp.getName()!=null && cp.getName().toLowerCase().contains(searchName.toLowerCase())){
                                curList.add(cp);
                            }
                        }
                    }else{
                        curList = cList;
                    }
//                    JSONObject remainNode = channel.computePoolQueryResource(uuid);
//                    JSONArray remainList = remainNode.getJSONArray("compute_resources");
                    if(computerList.size()>0) {
                        flag = "yes";
                    }
                }
            
            model.addAttribute("remain",flag);
            model.addAttribute("computerPoolDetail", curList);
            model.addAttribute("poolId", uuid);
            model.addAttribute("curNum", curNum);
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "resourcepool/resource_pool_manage_detail";
    }
    
    
    /**
     * 跳转到主机列表页
     * @param uuid
     * @param model
     * @param request
     * @return
     */
    @RequestMapping(value="/{uuid}/{name}/hpdetail",method=RequestMethod.GET)
    public String hostPoolDetail(@PathVariable("uuid") String uuid,@PathVariable("name") String name,Model model,HttpServletRequest request){
        if( ! new TransFormPrivilegeUtil().isHasPrivilege(request, TransFormPrivilegeConstant.desktop_resource_host_query)){
            return "not_have_access";
        }
        try {
                 List<CloudHostVO> cList = new ArrayList<>();
                HttpGatewayChannelExt channel = HttpGatewayManager.getChannel(1);
                if(channel!=null){
                	JSONObject result = null;
                	result = channel.hostQuery(uuid,"",0);
                	/*if("1".equals(name))
                		result = channel.hostQuery(uuid,"",0);
                	else
                		result = channel.hostQuery(uuid,name,2);*/
                    
                    if("success".equals(result.get("status"))){
                        
                        JSONArray computerList = result.getJSONArray("hosts");
                        for (int i = 0; i < computerList.size(); i ++) {
                            JSONObject computerObject = computerList.getJSONObject(i);
                            String hostName = computerObject.getString("name");
                            String uid = computerObject.getString("name");
                            int status = computerObject.getInt("status");
                            Integer cpuCount = computerObject.getInt("cpu_count");
                            BigDecimal cpuUsage = new BigDecimal(computerObject.getString("cpu_usage"));
                            BigDecimal memoryUsage = new BigDecimal(computerObject.getString("memory_usage"));
                            BigDecimal diskUsage = new BigDecimal(computerObject.getString("disk_usage"));
                            
                            JSONArray memoryList = computerObject.getJSONArray("memory");
                            BigInteger[] mcount = new BigInteger[memoryList.size()];
                            for(int j=0;j<memoryList.size();j++){
                                mcount[j] = new BigInteger(memoryList.getString(j));
                            }
                            
                            JSONArray diskList = computerObject.getJSONArray("disk_volume");
                            BigInteger[] dcount = new BigInteger[diskList.size()];
                            for(int j=0;j<diskList.size();j++){
                                dcount[j] = new BigInteger(diskList.getString(j));
                            }
                            
                            JSONArray ips = computerObject.getJSONArray("ip");
                            String[] hostIps = new String[ips.size()];
                            for(int j=0;j<ips.size();j++){
                                hostIps[j] = ips.getString(j);
                            }
                            CloudHostVO computer = new CloudHostVO();
                            computer.setCpuCore(cpuCount);
                            computer.setCpuUsage(cpuUsage.doubleValue());
                            computer.setDiskUsage(diskUsage.doubleValue());
                            computer.setRemainMemory(mcount[1].subtract(mcount[0]));
                            computer.setSysDisk(dcount[1]);
                            computer.setRemainDisk(dcount[1].subtract(dcount[0]));
                            computer.setMemory(mcount[1]);
                            computer.setMemoryUsage(memoryUsage.doubleValue());
                            computer.setHostName(hostName);
                            computer.setDisplayName(hostName);
                            computer.setStatus(status);
                            if(!"".equals(uid)){
                            	computer.setId(uid.substring(0, uid.length()-1));
                                computer.setRealHostId(uid.substring(0, uid.length()-1));
                            }
                            if(hostIps!=null && hostIps.length == 1){
                                computer.setInnerIp(hostIps[0]);
                            }else if(hostIps!=null && hostIps.length == 2){
                                computer.setInnerIp(hostIps[0]);
                                computer.setOuterIp(hostIps[1]);                                
                            }
                             cList.add(computer);
                        }
                    }
                    if ("fail".equals(result.getString("status"))){
                        logger.error("ResourcePoolController.hostPoolDetail>>>获取资源池失败");
                        return "not_responsed";
                    }
                }
                
                if(cList.size()>0){
                    for(CloudHostVO cp : cList){
                        CloudHostVO cloudHost = cloudHostService.getByRealHostId(cp.getRealHostId());
                        if(cloudHost!=null){
                            // 设置主机显示名称和类型
                            cp.setDisplayName(cloudHost.getDisplayName());
                            cp.setType(cloudHost.getType());
                        } 
                        
                    }
                }
            model.addAttribute("hostPoolDetail", cList);
            model.addAttribute("uuid", uuid);
            int poolType = 0;// 0表示默认
            if(name.contains("server_pool")){
                poolType = 1; //1表示云服务器的资源池
            }else if(name.contains("desktop_pool")){
                poolType = 2; // 2表示云桌面的资源池
                //筛选仓库
                model.addAttribute("wareHoseList",CloudHostWarehouseService.getAll());
            }
            model.addAttribute("poolType", poolType);
 
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "desktop/host_pool_detail";
    }
    
    @RequestMapping(value="/{uuid}/{name}/hpdetails",method=RequestMethod.GET)
    public String hostPoolDetails(@PathVariable("uuid") String uuid,@PathVariable("name") String name,Model model,HttpServletRequest request){
        if( ! new TransFormPrivilegeUtil().isHasPrivilege(request, TransFormPrivilegeConstant.desktop_resource_host_query)){
            return "not_have_access";
        }
        try {
                 List<CloudHostVO> cList = new ArrayList<>();
                HttpGatewayChannelExt channel = HttpGatewayManager.getChannel(1);
                if(channel!=null){
                	JSONObject result = null;
                	result = channel.hostQuery(uuid,name,2);
                    
                    if("success".equals(result.get("status"))){
                        
                        JSONArray computerList = result.getJSONArray("hosts");
                        for (int i = 0; i < computerList.size(); i ++) {
                            JSONObject computerObject = computerList.getJSONObject(i);
                            String hostName = computerObject.getString("name");
                            String uid = computerObject.getString("name");
                            int status = computerObject.getInt("status");
                            Integer cpuCount = computerObject.getInt("cpu_count");
                            BigDecimal cpuUsage = new BigDecimal(computerObject.getString("cpu_usage"));
                            BigDecimal memoryUsage = new BigDecimal(computerObject.getString("memory_usage"));
                            BigDecimal diskUsage = new BigDecimal(computerObject.getString("disk_usage"));
                            
                            JSONArray memoryList = computerObject.getJSONArray("memory");
                            BigInteger[] mcount = new BigInteger[memoryList.size()];
                            for(int j=0;j<memoryList.size();j++){
                                mcount[j] = new BigInteger(memoryList.getString(j));
                            }
                            
                            JSONArray diskList = computerObject.getJSONArray("disk_volume");
                            BigInteger[] dcount = new BigInteger[diskList.size()];
                            for(int j=0;j<diskList.size();j++){
                                dcount[j] = new BigInteger(diskList.getString(j));
                            }
                            
                            JSONArray ips = computerObject.getJSONArray("ip");
                            String[] hostIps = new String[ips.size()];
                            for(int j=0;j<ips.size();j++){
                                hostIps[j] = ips.getString(j);
                            }
                            CloudHostVO computer = new CloudHostVO();
                            computer.setCpuCore(cpuCount);
                            computer.setCpuUsage(cpuUsage.doubleValue());
                            computer.setDiskUsage(diskUsage.doubleValue());
                            computer.setRemainMemory(mcount[1].subtract(mcount[0]));
                            computer.setSysDisk(dcount[1]);
                            computer.setRemainDisk(dcount[1].subtract(dcount[0]));
                            computer.setMemory(mcount[1]);
                            computer.setMemoryUsage(memoryUsage.doubleValue());
                            computer.setHostName(hostName);
                            computer.setDisplayName(hostName);
                            computer.setStatus(status);
                            if(!"".equals(uid)){
                            	computer.setId(uid.substring(0, uid.length()-1));
                            	computer.setRealHostId(uid.substring(0, uid.length()-1));
                            }
                            
                            if(hostIps!=null && hostIps.length == 1){
                                computer.setInnerIp(hostIps[0]);
                            }else if(hostIps!=null && hostIps.length == 2){
                                computer.setInnerIp(hostIps[0]);
                                computer.setOuterIp(hostIps[1]);                                
                            }
                             cList.add(computer);
                        }
                    }
                    if ("fail".equals(result.getString("status"))){
                        logger.error("ResourcePoolController.hostPoolDetail>>>获取资源池失败");
                        return "not_responsed";
                    }
                }
                
                if(cList.size()>0){
                    for(CloudHostVO cp : cList){
                        CloudHostVO cloudHost = cloudHostService.getByRealHostId(cp.getRealHostId());
                        if(cloudHost!=null){
                            // 设置主机显示名称和类型
                            cp.setDisplayName(cloudHost.getDisplayName());
                            cp.setType(cloudHost.getType());
                        } 
                        
                    }
                }
            model.addAttribute("hostPoolDetail", cList);
            model.addAttribute("uuid", uuid);
            model.addAttribute("name", name);
            int poolType = 0;// 0表示默认
            if(name.contains("server_pool")){
                poolType = 1; //1表示云服务器的资源池
            }else if(name.contains("desktop_pool")){
                poolType = 2; // 2表示云桌面的资源池
                //筛选仓库
                model.addAttribute("wareHoseList",CloudHostWarehouseService.getAll());
            }
            model.addAttribute("poolType", poolType);
 
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "desktop/host_pool_detail";
    }
    
    /**
     * 跳转到创建资源池页面
     * @param model
     * @param request
     * @return
     */
    @RequestMapping(value="/add",method=RequestMethod.GET)
    public String addResourcePool(Model model,HttpServletRequest request){
      if( ! new TransFormPrivilegeUtil().isHasPrivilege(request, TransFormPrivilegeConstant.desktop_resource_pool_add)){
          return "not_have_access";
      }
        try {
            List<IpPoolVO> ipList = new ArrayList<>();
            List<PortPoolVO> portList = new ArrayList<>();
                HttpGatewayChannelExt channel = HttpGatewayManager.getChannel(1);
                if(channel!=null){
                    JSONObject result = channel.addressPoolQuery();

                    if ("success".equals(result.getString("status"))) {
                        JSONArray IpPoolList = result.getJSONArray("addressPools");
                        for (int i = 0; i < IpPoolList.size(); i++) {
                            JSONObject ipObject = IpPoolList.getJSONObject(i);
                            String name = ipObject.getString("name");
                            String uid = ipObject.getString("uuid");
                            int status = ipObject.getInt("status");

                            JSONArray countList = ipObject.getJSONArray("count");
                            Integer[] ccount = new Integer[countList.size()];
                            for (int j = 0; j < countList.size(); j++) {
                                ccount[j] = countList.getInt(j);
                            }

                            IpPoolVO vo = new IpPoolVO();
                            vo.setName(name);
                            vo.setStatus(status);
                            vo.setUuid(uid);
                            vo.setCount(ccount);
                            ipList.add(vo);
                        }
                    } else {
                        logger.error("ResourcePoolController.addResourcePool-> fail to get address pool  ");
                    }

                    JSONObject resultPort = channel.portPoolQuery();
                    if ("success".equals(resultPort.getString("status"))) {
                        JSONArray portPoolList = resultPort.getJSONArray("portPools");

                        for (int i = 0; i < portPoolList.size(); i ++) {
                            JSONObject portObject = portPoolList.getJSONObject(i);
                            String name = portObject.getString("name");
                            String uid = portObject.getString("uuid");
                            int status = portObject.getInt("status");

                            JSONArray countList = portObject.getJSONArray("count");
                            Integer[] pcount = new Integer[countList.size()];
                            for(int j=0;j<countList.size();j++){
                                pcount[j] = countList.getInt(j);
                            }

                            PortPoolVO vo = new PortPoolVO();
                            vo.setName(name);
                            vo.setStatus(status);
                            vo.setUuid(uid);
                            vo.setCount(pcount);
                            portList.add(vo);
                        }
                    } else {
                        logger.error("ResourcePoolController.addResourcePool-> fail to get port pool  ");
                    }
                }
            //获取共享存储路径
            String path = null;
            SharedMemoryVO sharedMemoryVO = sharedMemoryService.queryAvailable();
            if (sharedMemoryVO != null){
                path = sharedMemoryVO.getUrl();
            }
            model.addAttribute("path", path);
            model.addAttribute("ipList", ipList);
            model.addAttribute("portList", portList);
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        return "resourcepool/resource_pool_add";
    }
    
    /**
     * 创建资源池
     * @param computeInfoExt
     * @param prefixion
     * @param request
     * @return
     */
    @RequestMapping(value="/add",method=RequestMethod.POST)
    @ResponseBody
    public MethodResult addResourcePool(ComputeInfoExt computeInfoExt, String prefixion, HttpServletRequest request){
        if( ! new TransFormPrivilegeUtil().isHasPrivilege(request, TransFormPrivilegeConstant.desktop_resource_pool_add)){
            return new MethodResult(MethodResult.FAIL,"您没有新增资源池的权限，请联系管理员");
        }
        String name = prefixion+computeInfoExt.getName();
        Integer networkType = computeInfoExt.getNetworkType();
        String network = computeInfoExt.getNetwork();
        Integer diskType = computeInfoExt.getDiskType();
        String diskSource = computeInfoExt.getDiskSource();
        int mode0 = computeInfoExt.getMode0()== null ? 0:computeInfoExt.getMode0();
        int mode1 = computeInfoExt.getMode1();
        int mode2 = computeInfoExt.getMode2();
        int mode3 = computeInfoExt.getMode3()== null ? 0:computeInfoExt.getMode3();
        String path = computeInfoExt.getPath();

        if(StringUtil.isBlank(name)){
            return new MethodResult(MethodResult.FAIL,"资源池名不能为空");
        }
        if(StringUtil.isBlank(String.valueOf(networkType))){
            return new MethodResult(MethodResult.FAIL,"网络类型不能为空");
        }
        if(("1".equals(networkType) || "2".equals(networkType)) && StringUtil.isBlank(network)){
            return new MethodResult(MethodResult.FAIL,"IP或端口资源池不能为空");
        }
        if(StringUtil.isBlank(String.valueOf(diskType))){
            return new MethodResult(MethodResult.FAIL,"存储类型不能为空");
        }
        if(("1".equals(String.valueOf(diskType))) && StringUtil.isBlank(diskSource)){
            return new MethodResult(MethodResult.FAIL,"存储资源池不能为空");
        }

        if(StringUtil.isBlank(String.valueOf(mode0))){
            return new MethodResult(MethodResult.FAIL,"高可用选项不能为空");
        }
        if(StringUtil.isBlank(String.valueOf(mode1))){
            return new MethodResult(MethodResult.FAIL,"自动Qos选项不能为空");
        }

        if(StringUtil.isBlank(String.valueOf(mode2))){
            return new MethodResult(MethodResult.FAIL,"thin provioning选项不能为空");
        }
        if(StringUtil.isBlank(String.valueOf(mode3))){
            return new MethodResult(MethodResult.FAIL,"backing image选项不能为空");
        }
        try {

            Map<String, Object> data = new LinkedHashMap<String, Object>();
            data.put("name", name);
            data.put("network_type", networkType);
            data.put("network", network);
            data.put("disk_type", diskType);
            data.put("disk_source", diskSource);
            data.put("mode0", mode0);
            data.put("mode1", mode1);
            data.put("mode2", mode2);
            data.put("mode3", mode3);
            data.put("path", path);


            MethodResult result = computePoolService.createComputePoolSync(data);
            if("success".equals(result.status)){
                operLogService.addLog("桌面云资源池管理", "创建计算资源池", "1", "1", request);
                return new MethodResult(result.status, result.message);
            }

        }  catch (Exception e) {
            e.printStackTrace();
            operLogService.addLog("桌面云资源池管理", "修改计算资源池", "1", "2", request);
            return new MethodResult(MethodResult.FAIL,"资源池创建失败");
        }

        operLogService.addLog("桌面云资源池管理", "修改计算资源池", "1", "2", request);
        return new MethodResult(MethodResult.FAIL,"资源池创建失败");
    }
    
    /**
     * 检查名字是否可用
     * @param name
     * @return
     */
    @RequestMapping(value="/checkAvaliable",method=RequestMethod.POST)
    @ResponseBody
    public MethodResult checkAvaliable(String name){
        boolean flag = true;
        try {
            HttpGatewayChannelExt channel = HttpGatewayManager.getChannel(1);
            if(channel!=null){
                JSONObject result = channel.computePoolQuery();
        if ("fail".equals(result.getString("status"))) {
            return new MethodResult(MethodResult.FAIL,"获取资源池失败");
        }
                JSONArray computerList = result.getJSONArray("compute_pools");
                for (int i = 0; i < computerList.size(); i ++) {
                    JSONObject computerObject = computerList.getJSONObject(i);
                    String oldName = computerObject.getString("name");
                    if(oldName.equals(name)){
                        flag = false;
                        break;
                    }
                }
            }
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }   
        if(flag){
            return new MethodResult(MethodResult.SUCCESS,"资源池名可用");
        }
        return new MethodResult(MethodResult.FAIL,"资源池名已存在");
    }

    /**
     * 修改资源池页面
     * @author 张翔
     * @param uuid
     * @param request
     * @return
     */
    @RequestMapping(value="/{uuid}/mod",method=RequestMethod.GET)
    public String modifyResourcePool(@PathVariable("uuid") String uuid, Model model, HttpServletRequest request)
        throws IOException {
        try {
            HttpGatewayChannelExt channel = HttpGatewayManager.getChannel(1);
            ComputeInfoExt computeInfoExt = computePoolService.getComputePoolDetailSync(uuid);
            if(channel!=null) {

                if (computeInfoExt == null) {
                    logger.error("ResourcePoolController.modifyResourcePool>>>获取资源池失败");
                    return "not_responsed";
                }
//
                Integer[] mode = computeInfoExt.getMode();

                computeInfoExt.setMode0(computeInfoExt.getMode()[0]);
                computeInfoExt.setMode1(computeInfoExt.getMode()[1]);
                if (mode.length == 4) {
                    computeInfoExt.setMode2(computeInfoExt.getMode()[2]);
                    computeInfoExt.setMode3(computeInfoExt.getMode()[3]);
                }else {
                    computeInfoExt.setMode2(0);
                    computeInfoExt.setMode3(0);
                }

                List<IpPoolVO> ipList = new ArrayList<>();
                List<PortPoolVO> portList = new ArrayList<>();
                JSONObject resultIp = channel.addressPoolQuery();
                if ("fail".equals(resultIp.getString("status"))) {
                    logger.error("ResourcePoolController.modifyResourcePool>>>获取IP失败");
                    return "not_responsed";
                }
                JSONObject resultPort = channel.portPoolQuery();
                if ("fail".equals(resultPort.getString("status"))) {
                    logger.error("ResourcePoolController.modifyResourcePool>>>获取端口失败");
                    return "not_responsed";
                }

                JSONArray IpPoolList = resultIp.getJSONArray("addressPools");
                JSONArray portPoolList = resultPort.getJSONArray("portPools");
                for (int i = 0; i < IpPoolList.size(); i ++) {
                    JSONObject ipObject = IpPoolList.getJSONObject(i);
                    String name = ipObject.getString("name");
                    String uid = ipObject.getString("uuid");
                    int status = ipObject.getInt("status");

                    JSONArray countList = ipObject.getJSONArray("count");
                    Integer[] ccount = new Integer[countList.size()];
                    for(int j=0;j<countList.size();j++){
                        ccount[j] = countList.getInt(j);
                    }

                    IpPoolVO vo = new IpPoolVO();
                    vo.setName(name);
                    vo.setStatus(status);
                    vo.setUuid(uid);
                    vo.setCount(ccount);
                    ipList.add(vo);
                }
                for (int i = 0; i < portPoolList.size(); i ++) {
                    JSONObject portObject = portPoolList.getJSONObject(i);
                    String name = portObject.getString("name");
                    String uid = portObject.getString("uuid");
                    int status = portObject.getInt("status");

                    JSONArray countList = portObject.getJSONArray("count");
                    Integer[] pcount = new Integer[countList.size()];
                    for (int j = 0; j < countList.size(); j++) {
                        pcount[j] = countList.getInt(j);
                    }

                    PortPoolVO vo = new PortPoolVO();
                    vo.setName(name);
                    vo.setStatus(status);
                    vo.setUuid(uid);
                    vo.setCount(pcount);
                    portList.add(vo);
                }

                //获取共享存储路径
                String path = null;
                SharedMemoryVO sharedMemoryVO = sharedMemoryService.queryAvailable();
                if (sharedMemoryVO != null){
                    path = sharedMemoryVO.getUrl();
                }
                model.addAttribute("path", path);

                model.addAttribute("ipList", ipList);
                model.addAttribute("portList", portList);
                model.addAttribute("computeInfoExt", computeInfoExt);

                return "resourcepool/resource_pool_mod";
            }
        }catch (MalformedURLException e){
            e.printStackTrace();
        }
        return "not_responsed";
    }

    /**
     * 修改资源池
     * @author 张翔
     * @param computeInfoExt
     * @param request
     * @return
     */
    @RequestMapping(value="/mod",method=RequestMethod.POST)
    @ResponseBody
    public MethodResult modifyComputePool(ComputeInfoExt computeInfoExt, HttpServletRequest request){
        if( ! new TransFormPrivilegeUtil().isHasPrivilege(request, TransFormPrivilegeConstant.desktop_resource_pool_mod)){
            return new MethodResult(MethodResult.FAIL,"您没有修改资源池的权限，请联系管理员");
        }
        try {
            //  获取参数
            String uuid = computeInfoExt.getUuid();
            String name = computeInfoExt.getName();
            Integer networkType = computeInfoExt.getNetworkType();
            String network = computeInfoExt.getNetwork();
            Integer diskType = computeInfoExt.getDiskType();
            String diskSource = computeInfoExt.getDiskSource();
            int mode0 = computeInfoExt.getMode0()== null ? 0:computeInfoExt.getMode0();
            int mode1 = computeInfoExt.getMode1();
            int mode2 = computeInfoExt.getMode2();
            int mode3 = computeInfoExt.getMode3()== null ? 0:computeInfoExt.getMode3();
            String path = computeInfoExt.getPath();

            if(StringUtil.isBlank(uuid)){
                return new MethodResult(MethodResult.FAIL,"资源池uuid不能为空");
            }
            if(StringUtil.isBlank(name)){
                return new MethodResult(MethodResult.FAIL,"资源池名不能为空");
            }
            if(StringUtil.isBlank(String.valueOf(networkType))){
                return new MethodResult(MethodResult.FAIL,"网络类型不能为空");
            }
            if(("1".equals(networkType) || "2".equals(networkType)) && StringUtil.isBlank(network)){
                return new MethodResult(MethodResult.FAIL,"IP或端口资源池不能为空");
            }
            if(StringUtil.isBlank(String.valueOf(diskType))){
                return new MethodResult(MethodResult.FAIL,"存储类型不能为空");
            }
            if("1".equals(diskType) && StringUtil.isBlank(diskSource)){
                return new MethodResult(MethodResult.FAIL,"云存储不能为空");
            }
            if(StringUtil.isBlank(String.valueOf(mode0))){
                return new MethodResult(MethodResult.FAIL,"高可用选项不能为空");
            }
            if(StringUtil.isBlank(String.valueOf(mode1))){
                return new MethodResult(MethodResult.FAIL,"自动Qos选项不能为空");
            }

            Map<String, Object> data = new LinkedHashMap<String, Object>();
            data.put("uuid", uuid);
            data.put("name", name);
            data.put("network_type", networkType);
            data.put("network", network);
            data.put("disk_type", diskType);
            data.put("disk_source", diskSource);
            data.put("mode0", mode0);
            data.put("mode1", mode1);
            data.put("mode2", mode2);
            data.put("mode3", mode3);            
            data.put("path", path);
            MethodResult result = computePoolService.modifyComputePoolSync(data);
            if("success".equals(result.status)){
                operLogService.addLog("桌面云资源池管理", "修改计算资源池", "1", "1", request);
                return new MethodResult(MethodResult.SUCCESS,"资源池修改成功");
            }
        } catch (Exception e) {
            e.printStackTrace();
            operLogService.addLog("桌面云资源池管理", "修改计算资源池", "1", "2", request);
            return new MethodResult(MethodResult.FAIL,"资源池修改失败");
        }

        operLogService.addLog("桌面云资源池管理", "修改计算资源池", "1", "2", request);
        return new MethodResult(MethodResult.FAIL,"资源池修改失败");
    }

    /**
     * 删除资源池
   * @author 张翔
 
	 * @param uuid
	 * @param request
	 * @return
	 */
	@RequestMapping(value="/{uuid}/delete",method=RequestMethod.GET)
	@ResponseBody
	public MethodResult deleteComputePool(@PathVariable("uuid") String uuid,HttpServletRequest request){
       if( ! new TransFormPrivilegeUtil().isHasPrivilege(request, TransFormPrivilegeConstant.desktop_resource_pool_remove)){
          return new MethodResult(MethodResult.FAIL,"您没有删除资源池的权限，请联系管理员");
      }
      try {
          MethodResult result = computePoolService.removeComputePool(uuid);
          if("success".equals(result.status)){
              operLogService.addLog("桌面云资源池管理", "删除计算资源池", "1", "1", request);
              Iterator<String> its = MonitorConstant.hostsMap.keySet().iterator(); 
              while (its.hasNext()) {
                  String key = its.next();
                  String resourceid = key.split("∮")[0];                  
                  if(resourceid.equals(uuid)){
                      MonitorConstant.hostsMap.remove(key);  
                      break;
                  };
                  
              } 
              return new MethodResult(MethodResult.SUCCESS,"资源池删除成功");
          }
      } catch (Exception e) {
          e.printStackTrace();
      }
      operLogService.addLog("桌面云资源池管理", "删除计算资源池", "1", "2", request);
      return new MethodResult(MethodResult.FAIL,"资源池删除失败");
    }
    
    /**
     * 创建资源(节点)页面
     * @param uuid
     * @param model
     * @return
     */
    @RequestMapping(value="/{uuid}/an",method=RequestMethod.GET)
    public String addNodePage(HttpServletRequest request,@PathVariable("uuid") String uuid,Model model){
        if( ! new TransFormPrivilegeUtil().isHasPrivilege(request, TransFormPrivilegeConstant.desktop_resource_node_add)){
            return "not_have_access";
        }
        List<ComputeInfoExt> curList = new ArrayList<>();
        HttpGatewayChannelExt channel = HttpGatewayManager.getChannel(1);
        try {
            if(channel!=null){
                JSONObject result = channel.computePoolQueryResource("");
          if ("fail".equals(result.getString("status"))){
              logger.error("ResourcePoolController.addNodePage>>>获取资源池失败");
              return "not_responsed";
          }
                JSONArray computerList = result.getJSONArray("compute_resources");
                for (int i = 0; i < computerList.size(); i ++) {
                    JSONObject computerObject = computerList.getJSONObject(i);
                    String name = computerObject.getString("name");
                    String status = computerObject.getString("status");
                    if(status.equals("0")){
                        ComputeInfoExt computer = new ComputeInfoExt();
                        computer.setName(name);
                        curList.add(computer);
                    }
                    
                }
            }
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        model.addAttribute("nodeList", curList);
        model.addAttribute("poolId", uuid);
        return "resourcepool/resource_node_add";
    }
    
    /**
     * 创建资源(节点)
     * @param uuid
     * @param name
     * @return
     */
    @RequestMapping(value="/an",method=RequestMethod.POST)
    @ResponseBody
    public MethodResult addNode(HttpServletRequest request,String uuid,String name){
        if( ! new TransFormPrivilegeUtil().isHasPrivilege(request, TransFormPrivilegeConstant.desktop_resource_node_add)){
            return new MethodResult(MethodResult.FAIL,"您没有添加资源节点的权限，请联系管理员");
        }
        if(StringUtil.isBlank(uuid)){
            return new MethodResult(MethodResult.FAIL,"资源池ID不能为空");
        }
        if(StringUtil.isBlank(name)){
            return new MethodResult(MethodResult.FAIL,"节点名不能为空");
        }
        try {
                HttpGatewayChannelExt channel = HttpGatewayManager.getChannel(1);
                if(channel!=null){
                    JSONObject result = channel.computePoolAddResource(uuid, name);
                    if("success".equals(result.get("status"))){
                        return new MethodResult(MethodResult.SUCCESS,"创建成功");
                    }
                    return new MethodResult(MethodResult.FAIL,"创建失败");
                }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new MethodResult(MethodResult.FAIL,"创建失败");
    }
    
    /**
     * 删除资源(节点)
     * @param name
     * @param ip
     * @param poolId
     * @return
     */
    @RequestMapping(value="/dn",method=RequestMethod.POST)
    @ResponseBody
    public MethodResult deleteNode(HttpServletRequest request,@RequestParam("name") String name,
            @RequestParam("ip") String ip,
            @RequestParam("poolId") String poolId){
        try {
            if( ! new TransFormPrivilegeUtil().isHasPrivilege(request, TransFormPrivilegeConstant.desktop_resource_node_delete)){
                return new MethodResult(MethodResult.FAIL,"您没有删除资源节点的权限，请联系管理员");
            }
                HttpGatewayChannelExt channel = HttpGatewayManager.getChannel(1);
                if(channel!=null){
                    JSONObject result = channel.hostQuery(poolId,"",0);
                    if("success".equals(result.get("status"))){
                        //查询资源池中的所以主机
                        JSONArray computerList = result.getJSONArray("hosts");
                        for (int i = 0; i < computerList.size(); i ++) {
                            JSONObject computerObject = computerList.getJSONObject(i);
                            JSONArray ips = computerObject.getJSONArray("ip");
                            //判断是否为该NC的主机
                            if(ip.equals(ips.getString(0))){
                                //如果是，提示删除失败，请先删除NC中的云主机
                            	return new MethodResult(MethodResult.FAIL,"资源移除失败，请删除资源中的云主机");
                            }
                        }
                        //nc中没有云主机则删除nc
                        JSONObject dnr = channel.computePoolRemoveResource(poolId, name);
                        if("success".equals(dnr.getString("status"))){
                            return new MethodResult(MethodResult.SUCCESS,"资源移除成功");
                        }else{
                        	return new MethodResult(MethodResult.FAIL,"资源删除失败");
                        }
                    }
                }
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new MethodResult(MethodResult.FAIL,"资源删除失败");
    }
    
    /**
     * 
    * @Title: deleteHosts 
    * @Description: 批量操作主机，关机重启，开机
    * @param @param ids
    * @param @param request
    * @param @return      
    * @return MethodResult     
    * @throws
     */
    @RequestMapping(value="/cloudhost/batchHosts",method=RequestMethod.POST)
    @ResponseBody
    public MethodResult BatchHosts(String ids,String operatorType,HttpServletRequest request){
        if( ! new TransFormPrivilegeUtil().isHasPrivilege(request, TransFormPrivilegeConstant.desktop_host_manage_delete)){
            return new MethodResult(MethodResult.FAIL,"您没有操作主机的权限，请联系管理员");
        }
        if(StringUtil.isBlank(ids)){
            return new MethodResult(MethodResult.FAIL, "未选择主机");            
        }
        
        String [] hostIds = ids.split(",");
        //String [] curIds = cur_ids.split(",");
        MethodResult mr = null;
        //循环操作
        for(String id:hostIds){
        	//operatorType1:开机  2:关机   3:重启
        	mr = cloudHostService.operatorCloudHostByRealHostId(id, operatorType,false,0);
        }
        
        return mr;
    }
    
    /**
     * 
     * @Title: startCloudHost 
     * @Description: 主机迁移页面
     * @param @param id
     * @param @param request
     * @param @return      
     * @return MethodResult     
     * @throws
     */
    @RequestMapping(value="/{id}/{uid}/{poolUuid}/hostMigration",method=RequestMethod.GET)
    public String hostMigration(@PathVariable("id") String id,@PathVariable("uid") String uid,@PathVariable("poolUuid") String poolUuid,HttpServletRequest request,Model model){
    	if( ! new TransFormPrivilegeUtil().isHasPrivilege(request, TransFormPrivilegeConstant.desktop_resource_host_query)){
            return "not_have_access";
        }
    	
    	CloudHostVO cloudHost = cloudHostService.getById(id);
    	String nodeName = request.getParameter("nodeName");
    	 model.addAttribute("cloudHost", cloudHost);
    	 model.addAttribute("uuid", uid);
    	 model.addAttribute("poolUuid", poolUuid);
    	 String nameList = "";
    	 
    	 try {
    		 List<ComputeInfoExt> cList = new ArrayList<>();
         	 HttpGatewayChannelExt channel = HttpGatewayManager.getChannel(1);
             if(channel!=null){
             	JSONObject result = channel.computePoolQueryResource(poolUuid);
                if ("fail".equals(result.getString("status"))){
                	logger.error("ResourcePoolController.getAll>>>获取资源池失败");
                    return "not_responsed";
                }
             
                JSONArray computerList = result.getJSONArray("compute_resources");
                for (int i = 0; i < computerList.size(); i ++) {
                    JSONObject computerObject = computerList.getJSONObject(i);
                    String name = computerObject.getString("name");
                    
                    if(nodeName.equals(name))
                    	continue;
                    
                    String ip = computerObject.getString("ip");
                    int status = computerObject.getInt("status");
                    Integer cpuCount = computerObject.getInt("cpu_count");
                    BigDecimal cpuUsage = new BigDecimal(computerObject.getString("cpu_usage"));
                    BigDecimal memoryUsage = new BigDecimal(computerObject.getString("memory_usage"));
                    BigDecimal diskUsage = new BigDecimal(computerObject.getString("disk_usage"));
                    
                    JSONArray memoryList = computerObject.getJSONArray("memory");
                    BigInteger[] mcount = new BigInteger[memoryList.size()];
                    for(int j=0;j<memoryList.size();j++){
                        mcount[j] = new BigInteger(memoryList.getString(j));
                    }
                    
                    JSONArray diskList = computerObject.getJSONArray("disk_volume");
                    BigInteger[] dcount = new BigInteger[diskList.size()];
                    for(int j=0;j<diskList.size();j++){
                        dcount[j] = new BigInteger(diskList.getString(j));
                    }
                    
                    ComputeInfoExt computer = new ComputeInfoExt();
                    computer.setCpuCount(cpuCount);
                    computer.setCpuUsage(cpuUsage);
                    computer.setDiskUsage(diskUsage);
                    computer.setDiskVolume(dcount);
                    computer.setMemory(mcount);
                    computer.setMemoryUsage(memoryUsage);
                    computer.setName(name);
                    computer.setStatus(status);
                    computer.setIp(ip);
                    nameList = nameList + name+",";
                    cList.add(computer);
                }
             }
             
             model.addAttribute("nameList", nameList.substring(0, nameList.length()-1));
             model.addAttribute("computerPool", cList);
         } catch (MalformedURLException e) {
             e.printStackTrace();
         } catch (IOException e) {
             e.printStackTrace();
         }
    	 
    	 if(!"".equals(id))
    		 model.addAttribute("typeString", "1");
    	 else
    		 model.addAttribute("typeString", "0");
    	 
    	 model.addAttribute("realHostId", id);
    	 
    	return "desktop/hostMigration";
    }
    
    /**
     * 调整到主机迁移记录页面
     * @param id
     * @param request
     * @param model
     * @return
     */
    @RequestMapping(value="/{id}/queryHostMigration",method=RequestMethod.GET)
    public String queryHostMigration(@PathVariable("id") String id,HttpServletRequest request,Model model){
    	 model.addAttribute("typeString", id);
    	 
    	 List<HostMigrationVO> hmlist = hostMigrateionService.getHostMigration();
    	 model.addAttribute("hmlist", hmlist);
    	return "desktop/hostMigrationList";
    }
    
    /**
     * 迁移主机记录删除
     * @param id
     * @param model
     * @param request
     * @return
     */
    @RequestMapping(value="/{id}/delMigration",method=RequestMethod.GET)
    @ResponseBody
    public MethodResult delMigration(@PathVariable("id") String id,@PathVariable("time") String time,Model model,HttpServletRequest request){
    	
    	MethodResult mr = new MethodResult(MethodResult.FAIL,"删除失败");
        
        if(!"".equals(id) && null !=id){
        	if(hostMigrateionService.delById(id,time)>0){
        		mr = new MethodResult(MethodResult.SUCCESS,"删除成功");
        	}
        }
        
        return mr;
    }
    
    /**
     * 主机迁移提交
     * @param request
     * @param model
     * @return
     */
    @RequestMapping(value="/toMigration",method=RequestMethod.POST)
    @ResponseBody
    public MethodResult toMigrate(HttpServletRequest request,Model model){
    	if( ! new TransFormPrivilegeUtil().isHasPrivilege(request, TransFormPrivilegeConstant.desktop_resource_pool_host_start)){
            return new MethodResult(MethodResult.FAIL,"您没有迁移的权限，请联系管理员");
        }
    	
    	String ids = request.getParameter("id");
    	String realHostId = request.getParameter("realHostId");
    	String localhostNC = request.getParameter("innerIp");
    	String uuid1 = request.getParameter("uuid");
    	String nodeName = request.getParameter("nodeName");
    	Integer hostType = 0;
    	
    	Map<String,Object> condition = new LinkedHashMap<String, Object>();
    	condition.put("id", StringUtil.generateUUID());
    	condition.put("realHostId", realHostId);
    	condition.put("hostName", nodeName);
    	condition.put("localhostNC", localhostNC);
//    	condition.put("toNC", localhostNC);
    	condition.put("time", StringUtil.dateToString(new Date(), "yyyyMMddHHmmssSSS"));
    	condition.put("status", hostType);
    	
    	String sessionId = null;
    	
    	MigrationProgressPool mpoo = MigrationProgressPoolManager.singleton().getPool();
    	MigrationProgressData data =mpoo.get(realHostId);
    	
    	if(data!=null &&data.getResetStatus()!=null&& data.getResetStatus() ==1 ) {
            operLogService.addLog("云主机", "迁移主机"+data.getDisplayName()+"失败", "1", "2", request);
            return new MethodResult(MethodResult.FAIL, "正在迁移中 请完成后再进行此操作");
        }
    	
        
    	MethodResult mr = null;
    	if(ids!=null && !"".equals(ids)){
    		String toNC = "";
			CloudHostVO host = cloudHostService.queryCloudHostById(uuid1);
			HttpGatewayChannelExt channel = HttpGatewayManager.getChannel(1);
            if(channel!=null){
            	JSONObject result;
				try {
					result = channel.hostQuery(ids,"",0);
					
					if("success".equals(result.get("status"))){
						JSONArray computerList = result.getJSONArray("hosts");
						for (int i = 0; i < computerList.size(); i ++) {
                            JSONObject computerObject = computerList.getJSONObject(i);
                            JSONArray ips = computerObject.getJSONArray("ip");
                            String[] hostIps = new String[ips.size()];
                            for(int j=0;j<ips.size();j++){
                                hostIps[j] = ips.getString(j);
                            }
                            if(hostIps!=null && hostIps.length > 0){
                            	toNC = hostIps[0];
                            }
                            if(!"".equals(toNC)){
                            	condition.put("toNC",toNC);
                            	break;
                            }
						}

					}
					
					sessionId = this.migrate(request,realHostId, nodeName, hostType,condition);
					if (sessionId == null) {
		                logger.warn("CloudHostServiceImpl.toMigrate() > [" + Thread.currentThread().getId()
		                    + "] rest migrate failed");
		                operLogService.addLog("云主机", "迁移主机"+host.getDisplayName()+"失败", "1", "2", request);
		                throw new AppException("迁移主机命令发送失败");
		            }else{
		            	mr = new MethodResult(MethodResult.SUCCESS, "迁移命令发送成功");
		            }
					
					//存入缓存
		            Map<String, Object> temporaryData = new LinkedHashMap<String, Object>();
		            temporaryData.put("uuid", realHostId);
		            temporaryData.put("session_id", sessionId);
		            
		            AppInconstant.migrateProgress.put(realHostId + "_migrate_progress", temporaryData);
		            operLogService.addLog("云主机", "迁移主机"+host.getDisplayName()+"成功", "1", "1", request);
		            
		            model.addAttribute("realHostId", realHostId);
		            model.addAttribute("sessionId", sessionId);
					
				} catch (MalformedURLException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
            }
    		
    	}else{
    		return new MethodResult(MethodResult.FAIL, "迁移失败");
    	}
    	
    	return mr;
    }
    
    /**
     * 
     * @param uuid 云主机uuid
     * @param node_name 目的nc的node_name，可以为空
     * @param type 迁移类型；0=cold<默认>，1=warn，2=hot
     * @param callback http_gateway回推异步消息url地址，推送消息类型：“migrate_host_ack”，“migrate_host_report”，“migrate_host_result”（具体消息格式请参考消息推送章节）
     * @return
     * @throws MalformedURLException
     */
	public String migrate(HttpServletRequest request,String uuid, String node_name, int type,Map<String,Object> condition) throws MalformedURLException, IOException {
		HttpGatewayAsyncChannel channel = HttpGatewayManager.getAsyncChannel(1);
		try {

			JSONObject result = channel.hostFlushDiskMigration(uuid, node_name, type);
			if (HttpGatewayResponseHelper.isSuccess(result) == true) {
				System.err.println("success to send flush disk migrate request.");
				MigrationProgressPool pool = MigrationProgressPoolManager.singleton().getPool();
				MigrationProgressData migrate = pool.get(uuid);
				
				if(null ==migrate){
					migrate = new MigrationProgressData();
					migrate.setRealHostId(uuid);
					migrate.setSessionId(null);
					migrate.setResetStatus(1);
				}
				//修改状态，迁移中;
				condition.put("status", 1);
				cloudHostService.operatorMigration(uuid, condition);
				
				return channel.getSessionId();
			} else {
				condition.put("status", 3);
				cloudHostService.operatorMigration(uuid, condition);
				System.err.println("fail to send flush disk migrate request.");
				channel.release();
			}
		} catch (Exception e) {
			System.err.println("fail to send flush disk migrate request.");
			channel.release();
			throw e;
		}
		return null;
	}
	
	/**
     * 调整到主机迁移记录页面
     * @param id
     * @param request
     * @param model
     * @return
     */
    @RequestMapping(value="/{id}/getostMigrationPree",method=RequestMethod.GET)
    public String getostMigrationPree(@PathVariable("id") String id,HttpServletRequest request,Model model){
    	 model.addAttribute("realHostId", id);
    	return "desktop/flush_disk_migration";
    }
    
    /**
     * 
    * @Title: getFlushHostResult 
    * @Description: 获取迁移进度
    * @param @param uuid
    * @param @return      
    * @return MethodResult     
    * @throws
     */
    @RequestMapping(value="/cloudserver/getflushprogress",method=RequestMethod.GET)
    @ResponseBody
    public MethodResult getFlushHostResult(HttpServletRequest request,String uuid) {
        MethodResult result = new MethodResult(MethodResult.SUCCESS, "迁移成功");

        try {
            /*@SuppressWarnings("unchecked") 
            Map<String, Object> temporaryData = (Map<String, Object>) AppInconstant.migrateProgress.get(uuid + "_migrate_progress");
            
            if (temporaryData == null) {
                throw new AppException("无法获取迁移信息");
            }*/

            MigrationProgressPool pool = MigrationProgressPoolManager.singleton().getPool();

            MigrationProgressData data = pool.get(uuid);
            if (data == null) {
                MethodResult message = new MethodResult(MethodResult.FAIL, "迁移还未开始");
                return message;
            }
            else if(data.getResetStatus() == 0 ){
                if(data.isSuccess()){
                    result.put("progress", 100);
                    result.put("migrate_status", true);
                    return result;
 
                }else{
                    result.put("migrate_status", false);
                    return result;
                }
            }

            if (data.isReady()) {
                if (data.isFinished()) {
                    if (data.isSuccess()) {
                        result.put("progress", 100);
                        result.put("migrate_status", true);
                        //迁移完成后修改状态
                        cloudHostService.updateHostMigrationById(uuid,StringUtil.dateToString(new Date(), "yyyyMMddHHmmssSSS"),2);
                    } else {
                        result.put("migrate_status", false);
                    }
                } else {
                    result.put("progress", data.getProgress());
                }
                return result;
            }

            MethodResult message = new MethodResult(MethodResult.FAIL, "迁移还未开始");
            return message;
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            throw new AppException(e);
        }
    }
    
    /**
    * 
    * @Title: startCloudHost 
    * @Description: 主机开机
    * @param @param id
    * @param @param request
    * @param @return      
    * @return MethodResult     
    * @throws
    */
    @RequestMapping(value="/{id}/start",method=RequestMethod.GET)
    @ResponseBody
    public MethodResult startCloudHost(@PathVariable("id") String id,HttpServletRequest request){
        if( ! new TransFormPrivilegeUtil().isHasPrivilege(request, TransFormPrivilegeConstant.desktop_resource_pool_host_start)){
            return new MethodResult(MethodResult.FAIL,"您没有开机的权限，请联系管理员");
        }
        MethodResult mr = cloudHostService.operatorCloudHostByRealHostId(id, "1",false,0);
        return mr;
    }
    /**
     * 
    * @Title: stopCloudHost 
    * @Description: 主机关机 
    * @param @param id
    * @param @param request
    * @param @return      
    * @return MethodResult     
    * @throws
     */
    @RequestMapping(value="/{id}/stop",method=RequestMethod.GET)
    @ResponseBody
    public MethodResult stopCloudHost(@PathVariable("id") String id,HttpServletRequest request){
        if( ! new TransFormPrivilegeUtil().isHasPrivilege(request, TransFormPrivilegeConstant.desktop_resource_pool_host_stop)){
            return new MethodResult(MethodResult.FAIL,"您没有关机的权限，请联系管理员");
        }
        MethodResult mr = cloudHostService.operatorCloudHostByRealHostId(id, "2",false,0);
        return mr;
    }
    /**
     * 
    * @Title: restartCloudHost 
    * @Description: 主机重启
    * @param @param id
    * @param @param request
    * @param @return      
    * @return MethodResult     
    * @throws
     */
    @RequestMapping(value="/{id}/restart",method=RequestMethod.GET)
    @ResponseBody
    public MethodResult restartCloudHost(@PathVariable("id") String id,HttpServletRequest request){
        if( ! new TransFormPrivilegeUtil().isHasPrivilege(request, TransFormPrivilegeConstant.desktop_resource_pool_host_restart)){
            return new MethodResult(MethodResult.FAIL,"您没有重启的权限，请联系管理员");
        }
        MethodResult mr = cloudHostService.operatorCloudHostByRealHostId(id, "3",false,0);
        return mr;
    }
    /**
     * 
    * @Title: haltCloudHost 
    * @Description: 主机强制关机
    * @param @param id
    * @param @param request
    * @param @return      
    * @return MethodResult     
    * @throws
     */
    @RequestMapping(value="/{id}/halt",method=RequestMethod.GET)
    @ResponseBody
    public MethodResult haltCloudHost(@PathVariable("id") String id,HttpServletRequest request){
        if( ! new TransFormPrivilegeUtil().isHasPrivilege(request, TransFormPrivilegeConstant.desktop_resource_pool_host_halt)){
            return new MethodResult(MethodResult.FAIL,"您没有强制的权限，请联系管理员");
        }
        MethodResult mr = cloudHostService.operatorCloudHostByRealHostId(id, "4",false,0);
        return mr;
    }
    /**
     * 
    * @Title: addServer 
    * @Description: 添加主机进云服务器 
    * @param @param id
    * @param @param request
    * @param @return      
    * @return MethodResult     
    * @throws
     */
    @RequestMapping(value="/{id}/addserver",method=RequestMethod.GET)
    @ResponseBody
    public MethodResult addServer(@PathVariable("id") String id,HttpServletRequest request){
        if( ! new TransFormPrivilegeUtil().isHasPrivilege(request, TransFormPrivilegeConstant.compute_resource_pool_host_changetoserver)){
            return new MethodResult(MethodResult.FAIL,"您没有添加云服务器的权限，请联系管理员");
        }
        MethodResult mr = cloudHostService.addHostToServerByRealHostId(id);
        return mr;
    }
    /**
     * 
    * @Title: addDesktop 
    * @Description: 添加资源池主机进云桌面 
    * @param @param hostId
    * @param @param wareHouseId
    * @param @param request
    * @param @return      
    * @return MethodResult     
    * @throws
     */
    @RequestMapping(value="/{hostId}/{wareHouseId}/{uuid}/adddesktop",method=RequestMethod.GET)
    @ResponseBody
    public MethodResult addDesktop(@PathVariable("hostId") String hostId,@PathVariable("wareHouseId") String wareHouseId,@PathVariable("uuid") String uuid,HttpServletRequest request){
        if( ! new TransFormPrivilegeUtil().isHasPrivilege(request, TransFormPrivilegeConstant.desktop_resource_pool_host_changetodesktop)){
            return new MethodResult(MethodResult.FAIL,"您没有添加云服务器的权限，请联系管理员");
        }
        MethodResult mr = cloudHostService.addHostToDeskTopByRealHostId(hostId, wareHouseId,uuid);
        return mr;
    }
    
    /**
     * 
     * @Title: nodeDevice 
     * @Description: NC磁盘挂载
     * @param @param nodename
     * @param @param request
     * @param @return      
      */
 @RequestMapping(value="/{nodename}/device/{poolid}",method=RequestMethod.GET)
 public String nodeDevice(@PathVariable("nodename") String target,
         @PathVariable("poolid") String poolid,
         Model model,HttpServletRequest request){
     if( ! new TransFormPrivilegeUtil().isHasPrivilege(request, TransFormPrivilegeConstant.desktop_resource_node_device)){
         return "not_have_access";
     }   
     List<MountDiskVo> cList = new ArrayList<MountDiskVo>();
     try {
         HttpGatewayAsyncChannel channel = HttpGatewayManager.getAsyncChannel(Integer.valueOf(1)); 
         if(channel!=null){

             long reqTime = System.currentTimeMillis();
             JSONObject result = channel.serverQueryStorageDevice(MountDiskVo.LEVEL, target, MountDiskVo.DISK_TYPE_LOCALSTORAGE);  
             logger.info("HttpGatewayAsyncChannel.serverQueryStorageDevice>result:"+result.toString());
             if("success".equals(result.get("status"))){
                 List<MountDiskVo> list = StorageManager.singleton().getDiskVoList(reqTime, MountDiskVo.DISK_TYPE_LOCALSTORAGE);
                 cList.addAll(list);
             }

             reqTime = System.currentTimeMillis(); 
             channel = HttpGatewayManager.getAsyncChannel(Integer.valueOf(1)); 
             result = channel.serverQueryStorageDevice(MountDiskVo.LEVEL, target,MountDiskVo.DISK_TYPE_CLOUDSTORAGE );  
             if("success".equals(result.get("status"))){
                 List<MountDiskVo> list = StorageManager.singleton().getDiskVoList(reqTime, MountDiskVo.DISK_TYPE_CLOUDSTORAGE);
                 cList.addAll(list);
             }

             reqTime = System.currentTimeMillis(); 
             channel = HttpGatewayManager.getAsyncChannel(Integer.valueOf(1)); 
             result = channel.serverQueryStorageDevice(MountDiskVo.LEVEL, target, MountDiskVo.DISK_TYPE_NASDISK);  
             if("success".equals(result.get("status"))){
                 List<MountDiskVo> list = StorageManager.singleton().getDiskVoList(reqTime, MountDiskVo.DISK_TYPE_NASDISK);
                 cList.addAll(list);
             }
 
             reqTime = System.currentTimeMillis(); 
             channel = HttpGatewayManager.getAsyncChannel(Integer.valueOf(1)); 
             result = channel.serverQueryStorageDevice(MountDiskVo.LEVEL, target, MountDiskVo.DISK_TYPE_IPSAN);  
             if("success".equals(result.get("status"))){
                 List<MountDiskVo> list = StorageManager.singleton().getDiskVoList(reqTime, MountDiskVo.DISK_TYPE_IPSAN);
                 cList.addAll(list);
             }                
         }
     } catch (MalformedURLException e) {
         e.printStackTrace();
     } catch (IOException e) {
         e.printStackTrace();        
     }       
     model.addAttribute("formPageFlag", "0");
     model.addAttribute("poolId", poolid);
     model.addAttribute("deviceList", cList);
     model.addAttribute("nodename", target);     
     return "resourcepool/resource_pool_node_device";
 }
 
    /**
     * @Title: unmount
     * @Description: 取消NC磁盘挂载
     * @param @param request
     * @param @return
     */
    @RequestMapping(value = "/unmount", method = RequestMethod.POST)
    @ResponseBody
    public MethodResult unmountDevice(HttpServletRequest request) {
        if (!new TransFormPrivilegeUtil().isHasPrivilege(request,
                TransFormPrivilegeConstant.desktop_resource_node_unmount)) {
            return new MethodResult(MethodResult.FAIL, "您没有取消挂载硬盘的权限，请联系管理员");
        }

        String target = request.getParameter("nodename");
        Integer index = Integer.valueOf(request.getParameter("index"));
        Integer diskType = Integer.valueOf(request.getParameter("disk_type"));
        try {
            HttpGatewayAsyncChannel channel = HttpGatewayManager.getAsyncChannel(Integer.valueOf(1));
            if (channel == null) {
                operLogService.addLog("云计算资源池", "计算节点" + target + "取消挂载本地硬盘失败", "2", "2", request);
                return new MethodResult(MethodResult.FAIL, "取消挂载本地硬盘失败，请联系管理员");
            }
            long reqTime = System.currentTimeMillis();
            JSONObject result = channel.serverRemoveStorageDevice(MountDiskVo.LEVEL, target, diskType, index);
            if ("fail".equals(result.get("status"))) {
                operLogService.addLog("云计算资源池", "计算节点" + target + "取消挂载本地硬盘失败", "2", "2", request);
                return new MethodResult(MethodResult.FAIL, result.getString("message"));
            }
            // 等待异步处理结果。
            StorageResult ret = StorageManager.singleton().getRemoveStorageReslt(reqTime);
            if (!ret.isFlag()) {
                operLogService.addLog("云计算资源池", "计算节点" + target + "取消挂载本地硬盘失败，返回：" + ret.getMsg(), "2", "2", request);
                return new MethodResult(MethodResult.FAIL, ret.getMsg());
            }
        } catch (MalformedURLException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            operLogService.addLog("云计算资源池", "计算节点" + target + "取消挂载本地硬盘失败", "2", "2", request);
            return new MethodResult(MethodResult.FAIL, "取消挂载本地硬盘失败，请联系管理员");
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            operLogService.addLog("云计算资源池", "计算节点" + target + "取消挂载本地硬盘失败", "2", "2", request);
            return new MethodResult(MethodResult.FAIL, "取消挂载本地硬盘失败，请联系管理员");
        }
        operLogService.addLog("云服务器资源池", "计算节点" + target + "取消挂载硬盘成功", "2", "1", request);
        return new MethodResult(MethodResult.SUCCESS, "取消挂载硬盘成功！");
    }
    
    /**
     * @Title: mount
     * @Description: 磁盘挂载
     * @param @param request
     * @param @return
     */
    @RequestMapping(value = "/mount", method = RequestMethod.POST)
    @ResponseBody
    public MethodResult mountDevice(HttpServletRequest request) {
        if (!new TransFormPrivilegeUtil()
                .isHasPrivilege(request, TransFormPrivilegeConstant.desktop_resource_node_mount)) {
            return new MethodResult(MethodResult.FAIL, "您没有挂载硬盘的权限，请联系管理员");
        }

        String target = request.getParameter("nodename");
        Integer[] index = new Integer[] {Integer.valueOf(request.getParameter("index")) };
        Integer disk_type = Integer.valueOf(request.getParameter("disk_type"));
        try {
            HttpGatewayAsyncChannel channel = HttpGatewayManager.getAsyncChannel(Integer.valueOf(1));
            if (channel == null) {
                operLogService.addLog("云计算资源池", "计算节点" + target + "挂载本地硬盘失败", "2", "2", request);
                return new MethodResult(MethodResult.FAIL, "挂载本地硬盘失败，请联系管理员");
            }

            long reqTime = System.currentTimeMillis();
            JSONObject result = channel.serverAddStorageDevice(MountDiskVo.LEVEL, target, disk_type, index, "", "", "");
            if ("fail".equals(result.get("status"))) {
                operLogService.addLog("云计算资源池", "计算节点" + target + "挂载本地硬盘失败", "2", "2", request);
                return new MethodResult(MethodResult.FAIL, result.getString("message"));
            }

            // 等待异步处理结果。
            StorageResult ret = StorageManager.singleton().getAddStorageReslt(reqTime);
            if (!ret.isFlag()) {
                operLogService.addLog("云计算资源池", "计算节点" + target + "挂载本地硬盘失败，返回：" + ret.getMsg(), "2", "2", request);
                return new MethodResult(MethodResult.FAIL, ret.getMsg());
            }
        } catch (MalformedURLException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            operLogService.addLog("云计算资源池", "计算节点" + target + "挂载本地硬盘失败", "2", "2", request);
            return new MethodResult(MethodResult.FAIL, "挂载本地硬盘失败，请联系管理员");
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            operLogService.addLog("云计算资源池", "计算节点" + target + "挂载本地硬盘失败", "2", "2", request);
            return new MethodResult(MethodResult.FAIL, "挂载本地硬盘失败，请联系管理员");
        }
        operLogService.addLog("云计算资源池", "计算节点" + target + "挂载本地硬盘成功", "2", "1", request);
        return new MethodResult(MethodResult.SUCCESS, "挂载硬盘成功！");
    }
    
    /**
     * @Title: enableDisk
     * @Description: 启用本地存储设备
     * @param @param request
     * @param @return
     */
    @RequestMapping(value = "/enableDisk", method = RequestMethod.POST)
    @ResponseBody
    public MethodResult enableDisk(HttpServletRequest request) {
        if (!new TransFormPrivilegeUtil().isHasPrivilege(request,
                TransFormPrivilegeConstant.desktop_resource_node_enabledisk)) {
            return new MethodResult(MethodResult.FAIL, "您没有启用本地存储设备权限！");
        }
        String target = request.getParameter("nodename");
        Integer index = Integer.valueOf(request.getParameter("index"));

        try {
            HttpGatewayAsyncChannel channel = HttpGatewayManager.getAsyncChannel(Integer.valueOf(1));
            if (channel == null) {
                operLogService.addLog("云计算资源池", "计算节点" + target + "启用本地硬盘失败", "2", "2", request);
                return new MethodResult(MethodResult.FAIL, "启用本地硬盘失败，请联系管理员");
            }
            long reqTime = System.currentTimeMillis();
            JSONObject result = channel.serverEnableStorageDevice(MountDiskVo.LEVEL, target,
                    MountDiskVo.DISK_TYPE_LOCALSTORAGE, index);
            if ("fail".equals(result.get("status"))) {
                operLogService.addLog("云计算资源池", "计算节点" + target + "启用本地硬盘失败", "2", "2", request);
                return new MethodResult(MethodResult.FAIL, result.getString("message"));
            }
            // 等待异步处理结果。
            StorageResult ret = StorageManager.singleton().getEnableStorageReslt(reqTime);
            if (!ret.isFlag()) {
                operLogService.addLog("云计算资源池", "计算节点" + target + "启用本地硬盘失败，返回：" + ret.getMsg(), "2", "2", request);
                return new MethodResult(MethodResult.FAIL, ret.getMsg());
            }
        } catch (MalformedURLException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            operLogService.addLog("云计算资源池", "计算节点" + target + "启用本地硬盘失败", "2", "2", request);
            return new MethodResult(MethodResult.FAIL, "启用本地硬盘失败，请联系管理员");
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            operLogService.addLog("云计算资源池", "计算节点" + target + "启用本地硬盘失败", "2", "2", request);
            return new MethodResult(MethodResult.FAIL, "启用本地硬盘失败，请联系管理员");
        }

        operLogService.addLog("云计算资源池", "计算节点" + target + "启用本地存储设备成功", "2", "1", request);

        return new MethodResult(MethodResult.SUCCESS, "启用本地存储设备成功！");
    }
    
    /**
     * @Title: disableDisk
     * @Description: 禁用本地磁盘
     * @param @param request
     * @param @return
     */
    @RequestMapping(value = "/disableDisk", method = RequestMethod.POST)
    @ResponseBody
    public MethodResult disableDisk(HttpServletRequest request) {
        if (!new TransFormPrivilegeUtil().isHasPrivilege(request,
                TransFormPrivilegeConstant.desktop_resource_node_disabledisk)) {
            return new MethodResult(MethodResult.FAIL, "您没有禁用本地存储设备权限！");
        }
        String target = request.getParameter("nodename");
        Integer index = Integer.valueOf(request.getParameter("index"));

        try {
            HttpGatewayAsyncChannel channel = HttpGatewayManager.getAsyncChannel(Integer.valueOf(1));
            if (channel == null) {
                operLogService.addLog("云计算资源池", "计算节点" + target + "禁用本地硬盘失败", "2", "2", request);
                return new MethodResult(MethodResult.FAIL, "禁用本地硬盘失败，请联系管理员");
            }
            long reqTime = System.currentTimeMillis();
            JSONObject result = channel.serverDisableStorageDevice(MountDiskVo.LEVEL, target,
                    MountDiskVo.DISK_TYPE_LOCALSTORAGE, index);
            if ("fail".equals(result.get("status"))) {
                operLogService.addLog("云计算资源池", "计算节点" + target + "禁用本地硬盘失败", "2", "2", request);
                return new MethodResult(MethodResult.FAIL, result.getString("message"));
            }
            // 等待异步处理结果。
            StorageResult ret = StorageManager.singleton().getDisableStorageReslt(reqTime);
            if (!ret.isFlag()) {
                operLogService.addLog("云计算资源池", "计算节点" + target + "禁用本地硬盘失败,返回：" + ret.getMsg(), "2", "2", request);
                return new MethodResult(MethodResult.FAIL, ret.getMsg());
            }
        } catch (MalformedURLException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            operLogService.addLog("云计算资源池", "计算节点" + target + "禁用本地硬盘失败", "2", "2", request);
            return new MethodResult(MethodResult.FAIL, "禁用本地硬盘失败，请联系管理员");
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            operLogService.addLog("云计算资源池", "计算节点" + target + "禁用本地硬盘失败", "2", "2", request);
            return new MethodResult(MethodResult.FAIL, "禁用本地硬盘失败，请联系管理员");
        }
        operLogService.addLog("云计算资源池", "计算节点" + target + "禁用本地存储设备成功", "2", "1", request);
        return new MethodResult(MethodResult.SUCCESS, "禁用本地存储设备成功！");
    }
    
    /**
     * @Title: addShareDisk
     * @Description: 共享存储挂载
     * @param @param request
     * @param @return
     */
    @RequestMapping(value = "/addShareDisk", method = RequestMethod.POST)
    @ResponseBody
    public MethodResult addShareDisk(HttpServletRequest request) {
        if (!new TransFormPrivilegeUtil()
                .isHasPrivilege(request, TransFormPrivilegeConstant.desktop_resource_node_mount)) {
            return new MethodResult(MethodResult.FAIL, "您没有挂载共享存储的权限，请联系管理员");
        }

        String target = request.getParameter("nodename");
        Integer[] index = new Integer[] {Integer.valueOf(9) }; // 挂载网络的，不知道有没有要求？
        Integer disk_type = Integer.valueOf(request.getParameter("disk_type"));
        String name = request.getParameter("name");
        String path = request.getParameter("path");
        String crypt = request.getParameter("crypt");
        try {
            HttpGatewayAsyncChannel channel = HttpGatewayManager.getAsyncChannel(Integer.valueOf(1));
            if (channel == null) {
                operLogService.addLog("云计算资源池", "计算节点" + target + "挂载共享存储失败", "2", "2", request);
                return new MethodResult(MethodResult.FAIL, "挂载共享存储失败，请联系管理员");
            }
            long reqTime = System.currentTimeMillis();
            JSONObject result = channel.serverAddStorageDevice(MountDiskVo.LEVEL, target, disk_type, index, name, path,
                    crypt);
            if ("fail".equals(result.get("status"))) {
                operLogService.addLog("云计算资源池", "计算节点" + target + "挂载共享存储失败", "2", "2", request);
                return new MethodResult(MethodResult.FAIL, result.getString("message"));
            }
            // 等待异步处理结果。
            StorageResult ret = StorageManager.singleton().getAddStorageReslt(reqTime);
            if (!ret.isFlag()) {
                operLogService.addLog("云计算资源池", "计算节点" + target + "挂载共享存储失败，返回：" + ret.getMsg(), "2", "2", request);
                return new MethodResult(MethodResult.FAIL, ret.getMsg());
            }
        } catch (MalformedURLException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            operLogService.addLog("云计算资源池", "计算节点" + target + "挂载共享存储失败", "2", "2", request);
            return new MethodResult(MethodResult.FAIL, "挂载共享存储失败，请联系管理员");
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            operLogService.addLog("云计算资源池", "计算节点" + target + "挂载共享存储失败", "2", "2", request);
            return new MethodResult(MethodResult.FAIL, "挂载共享存储失败，请联系管理员");
        }
        operLogService.addLog("云计算资源池", "计算节点" + target + "挂载共享存储成功", "2", "1", request);
        return new MethodResult(MethodResult.SUCCESS, "挂载共享存储成功！");
    }
}
