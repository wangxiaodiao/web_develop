<%@ page pageEncoding="utf-8"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt"%>
<!DOCTYPE html>
<html>
<head>
    <title>控制台-${productName}</title>
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta charset="UTF-8" />

    <link rel="icon" type="image/ico" href="<%=request.getContextPath()%>/assets/images/favicon.ico" />
    <!-- Bootstrap -->
    <link href="<%=request.getContextPath()%>/assets/css/vendor/bootstrap/bootstrap.min.css" rel="stylesheet">
    <link href="<%=request.getContextPath()%>/font-awesome/css/font-awesome.css" rel="stylesheet">
    <link rel="stylesheet" href="<%=request.getContextPath()%>/assets/css/vendor/animate/animate.min.css">
    <link type="text/css" rel="stylesheet" media="all" href="<%=request.getContextPath()%>/assets/js/vendor/mmenu/css/jquery.mmenu.all.css" />
    <link rel="stylesheet" href="<%=request.getContextPath()%>/assets/js/vendor/videobackground/css/jquery.videobackground.css">
    <link rel="stylesheet" href="<%=request.getContextPath()%>/assets/css/vendor/bootstrap-checkbox.css">

    <link rel="stylesheet" href="<%=request.getContextPath()%>/assets/js/vendor/rickshaw/css/rickshaw.min.css">
    <link rel="stylesheet" href="<%=request.getContextPath()%>/assets/js/vendor/morris/css/morris.css">
    <link rel="stylesheet" href="<%=request.getContextPath()%>/assets/js/vendor/tabdrop/css/tabdrop.css">
    <link rel="stylesheet" href="<%=request.getContextPath()%>/assets/js/vendor/summernote/css/summernote.css">
    <link rel="stylesheet" href="<%=request.getContextPath()%>/assets/js/vendor/summernote/css/summernote-bs3.css">
    <link rel="stylesheet" href="<%=request.getContextPath()%>/assets/js/vendor/chosen/css/chosen.min.css">
    <link rel="stylesheet" href="<%=request.getContextPath()%>/assets/js/vendor/chosen/css/chosen-bootstrap.css">

    <link href="<%=request.getContextPath()%>/assets/css/zhicloud.css" rel="stylesheet">
    <link rel="stylesheet" href="<%=request.getContextPath() %>/editor/themes/default/default.css" />

    <!-- HTML5 Shim and Respond.js IE8 support of HTML5 elements and media queries -->
    <!-- WARNING: Respond.js doesn't work if you view the page via file:// -->
    <!--[if lt IE 9]>
    <script src="<%=request.getContextPath()%>/assets/js/html5shiv.js"></script>
    <script src="<%=request.getContextPath()%>/assets/js/respond.min.js"></script>
    <![endif]-->
</head>
<body class="bg-1">



<!-- Preloader -->
<div class="mask"><div id="loader"></div></div>
<!--/Preloader -->

<!-- Wrap all page content here -->
<div id="wrap">


    <!-- Make page fluid -->
    <div class="row">

        <%@include file="/views/common/common_menus.jsp" %>

        <!-- Page content -->
        <div id="content" class="col-md-12">



            <!-- page header -->
            <div class="pageheader">


                <h2><i class="fa fa-inbox"></i> 邮件发送记录详情</h2>


            </div>
            <!-- /page header -->


            <!-- content main container -->
            <div class="main">






                <!-- row -->
                <div class="row">

                    <div class="col-md-12">



                        <!-- tile -->
                        <section class="tile color transparent-black">



                            <!-- tile header -->
                            <div class="tile-header">
                                <h3><a href="<%=request.getContextPath() %>/message/record/sms/list"    style="color:#FAFAFA;cursor:pointer;padding-right:10px;"> <i class="fa fa-reply"></i></a>输入邮件模板信息</h3>
                            </div>
                            <!-- /tile header -->

                            <!-- tile body -->
                            <div class="tile-body">
                                <form class="form-horizontal" role="form">
                                <div class="form-group">
                                    <label for="name" class="col-sm-2 control-label" >发送人账号</label>
                                    <div class="col-sm-4">
                                        <input type="text" class="form-control" id="name" name="name" value="${message_record_vo.senderAddress}" readonly="readonly" parsley-trigger="change" parsley-required="true" parsley-minlength="5" parsley-maxlength="50">
                                    </div>
                                </div>

                                <div class="form-group">
                                    <label for="code" class="col-sm-2 control-label">收信人号码</label>
                                    <div class="col-sm-4">
                                        <input type="text" class="form-control" id="code" name="code" value="${message_record_vo.recipientAddress}" readonly="readonly" parsley-trigger="change" parsley-required="true" parsley-minlength="5" parsley-maxlength="50">
                                    </div>
                                </div>
                                <div class="form-group">
                                    <label for="sms_content" class="col-sm-2 control-label">短信正文</label>
                                    <div class="col-sm-4">
                                        <textarea class="note-editable" id="sms_content" name="content" style="width: 315px;" readonly="readonly" parsley-trigger="change" parsley-required="true" parsley-minlength="2" parsley-maxlength="50" parsley-validation-minlength="1">${message_record_vo.content}</textarea>
                                    </div>
                                </div>

                                    </form>
                            </div>
                            <!-- /tile body -->

                        </section>
                        <!-- /tile -->

                    </div>
                    <!-- /col 6 -->

                </div>
                <!-- /row -->

            </div>
            <!-- /content container -->

        </div>
        <!-- Page content end -->

    </div>
    <!-- Make page fluid-->

</div>
<!-- Wrap all page content end -->

<section class="videocontent" id="video"></section>
<script charset="utf-8" src="<%=request.getContextPath() %>/editor/kindeditor-min.js" type="text/javascript"></script>
<script charset="utf-8" src="<%=request.getContextPath() %>/editor/zh_CN.js" type="text/javascript"></script>
<script>
    var path = "<%=request.getContextPath()%>";

    $(function(){

        //check all checkboxes
        $('table thead input[type="checkbox"]').change(function () {
            $(this).parents('table').find('tbody input[type="checkbox"]').prop('checked', $(this).prop('checked'));
        });

        // sortable table
        $('.table.table-sortable th.sortable').click(function() {
            var o = $(this).hasClass('sort-asc') ? 'sort-desc' : 'sort-asc';
            $(this).parents('table').find('th.sortable').removeClass('sort-asc').removeClass('sort-desc');
            $(this).addClass(o);
        });

        //chosen select input
        $(".chosen-select").chosen({disable_search_threshold: 10});

        //check toggling
        $('.check-toggler').on('click', function(){
            $(this).toggleClass('checked');
        });

    });


    function saveForm(){
        jQuery.ajax({
            url: path+'/main/checklogin',
            type: 'post',
            dataType: 'json',
            timeout: 10000,
            async: true,
            error: function()
            {
                alert('Error!');
            },
            success: function(result)
            {
                if(result.status == "fail"){
                    $("#tipscontent").html("登录超时，请重新登录");
                    $("#dia").click();
                }else{
                    var options = {
                        success:function result(data){
                            if(data.status == "fail"){
                                $("#tipscontent").html("添加失败");
                                $("#dia").click();
                            }else{
                                location.href = path + "/message/sms/template/list";
                            }
                        },
                        dataType:'json',
                        timeout:10000
                    };
                    var form = jQuery("#sms_template_form");
                    form.parsley('validate');
                    if(form.parsley('isValid')){
                        jQuery("#sms_template_form").ajaxSubmit(options);
                    }
                }
            }
        });

    }

</script>

</body>
</html>
      
