const express = require('express');
const bcrypt = require('bcrypt');
const dotenv = require('dotenv');
const jwt = require('jsonwebtoken');
const router = express.Router();
const db = require('../config/db');
const { verifyToken } = require('../middleware/auth_middleware');
const { generateAccessToken, generateRefreshToken } = require('../utils/jwt');

const ACCESS_TOKEN_SECRET = process.env.ACCESS_TOKEN_SECRET;
const REFRESH_TOKEN_SECRET = process.env.REFRESH_TOKEN_SECRET;

dotenv.config();

// 이메일 중복 확인
router.get('/email', function(req, res) {
    const email = req.query.email;

    var sql='select * from member where email=?';
    db.query(sql, [email], function (err, data) {
        if(err) throw err;
        if(data.length > 0) {
            return res.status(204).json({
                success: "false",
                message: email + "은 이미 존재하는 이메일 주소입니다."
            });
        } else {
            return res.status(200).json({
                success: "true",
                message: email + "은 사용 가능한 이메일 주소입니다."
            });
        }
    })
})

// 회원가입
router.post('/register', async function(req, res) {
    const email = req.body.email;
    const password = req.body.password;
        
    const salt = await bcrypt.genSalt(10);
    const hashedPassword = await bcrypt.hash(password, salt);

    var sql = 'insert into member values (?,?)';
    db.query(sql, [email, hashedPassword], function (err, data) {
        if (err) throw err;
        return res.status(200).json({
            success: "true",
            message: "회원가입에 성공하였습니다."
        });
    });     
})   

// 로그인
router.post('/login', function(req, res) {
    const email = req.body.email;
    const password = req.body.password;

    var sql='select * from member where email=?';
    db.query(sql, [email], async function (err, data) {
        if(err) throw err;
        if(data.length !== 0) {
            var email = data[0].email;
            var hashedPassword = data[0].password;
            var verified = await bcrypt.compare(password, hashedPassword);

            if (verified) {
                const accessToken = generateAccessToken(email, hashedPassword);
                const refreshToken = generateRefreshToken(email);

                return res
                    .cookie('refreshToken', refreshToken, {httpOnly: true, maxAge: 1000000})
                    .status(200).json({
                        success: "true",
                        message: "로그인에 성공하였습니다. 토큰이 생성되었습니다.",
                        token: accessToken
                    });
            } else {
                return res.status(204).json({
                    success: "false",
                    message: "비밀번호가 일치하지 않습니다.",
                    token: ""
                });
            }
        } else {
            return res.status(400).json({
                success: "false",
                message: "존재하지 않는 이메일입니다.",
                token: ""
            });
        }
    })
});

// 로그아웃
router.get("/logout", verifyToken, (req, res) => {
    return res
        .clearCookie("refreshToken")
        .status(200).json({ 
            success: "true",
            message: "로그아웃에 성공했습니다."
    });
})

// 회원탈퇴
router.delete('/delete', verifyToken, function(req, res) {
    const email = req.email;

    var sql='delete from member where email=?';
    db.query(sql, [email], function (err, data) {
        if(err) throw err;
        if(data.affectedRows == 0) {
            return res.status(400).json({
                success: "false",
                message: "회원탈퇴에 실패했습니다."
            })
        } else {
            return res
            .clearCookie("refreshToken")
            .status(200).json({
                success: "true",
                message: "회원탈퇴에 성공하였습니다."
            })
        }
    });
})

// 회원정보 수정 (비밀번호 변경) 
router.post('/edit', verifyToken, async function(req, res) {
    const email = req.email;
    const old_password = req.body.old_password;
    const password = req.body.password;

    var verified = await bcrypt.compare(old_password, req.password);
    if (!verified) {
        return res.status(204).json({
            success: "false",
            message: "기존 비밀번호가 일치하지 않습니다.",
            token: ""
        });
    } else {
        var salt = await bcrypt.genSalt(10);
        var hashedPassword = await bcrypt.hash(password, salt);

        const accessToken = generateAccessToken(email, hashedPassword);

        var sql='update member set password=? where email=?';
        db.query(sql, [hashedPassword, email], async function (err, data) {
            return res.status(200).json({
                success: "true",
                message: "비밀번호 변경에 성공했습니다.",
                token: accessToken
            });
        })
    }
})

// Refresh Token을 이용한 Access Token 재발급
router.post('/token', (req, res) => {
    const refreshToken = req.cookies.refreshToken;
    if (!refreshToken) {
        return res.status(401).json({
            success: "false",
            message: "토큰 인증 오류입니다.",
            token: ""
        });
    }

    jwt.verify(refreshToken, REFRESH_TOKEN_SECRET, (err, email) => {
        if (err) {
            return res.status(403).json({
                success: "false",
                message: "토큰 인증 오류입니다.",
                token: ""
            });
        }
        const accessToken = generateAccessToken(email);
        return res.status(200).json({
                success: "true",
                message: "토큰이 정상적으로 발행되었습니다.",
                token: accessToken
            });
    })
})

module.exports = router;

